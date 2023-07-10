/**
 * Copyright © 2016-2023 The Comm360 Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.security.auth.mfa;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.security.model.mfa.PlatformTwoFaSettings;
import org.thingsboard.server.common.data.security.model.mfa.account.TwoFaAccountConfig;
import org.thingsboard.server.common.data.security.model.mfa.provider.TwoFaProviderConfig;
import org.thingsboard.server.common.data.security.model.mfa.provider.TwoFaProviderType;
import org.thingsboard.server.common.msg.tools.TbRateLimits;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.auth.mfa.config.TwoFaConfigManager;
import org.thingsboard.server.service.security.auth.mfa.provider.TwoFaProvider;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.system.SystemSecurityService;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
@TbCoreComponent
public class DefaultTwoFactorAuthService implements TwoFactorAuthService {

    private final TwoFaConfigManager configManager;
    private final SystemSecurityService systemSecurityService;
    private final UserService userService;
    private final Map<TwoFaProviderType, TwoFaProvider<TwoFaProviderConfig, TwoFaAccountConfig>> providers = new EnumMap<>(TwoFaProviderType.class);

    private static final ThingsboardException ACCOUNT_NOT_CONFIGURED_ERROR = new ThingsboardException("2FA is not configured for account", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
    private static final ThingsboardException PROVIDER_NOT_CONFIGURED_ERROR = new ThingsboardException("2FA provider is not configured", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
    private static final ThingsboardException PROVIDER_NOT_AVAILABLE_ERROR = new ThingsboardException("2FA provider is not available", ThingsboardErrorCode.GENERAL);

    private final ConcurrentMap<UserId, ConcurrentMap<TwoFaProviderType, TbRateLimits>> verificationCodeSendingRateLimits = new ConcurrentHashMap<>();
    private final ConcurrentMap<UserId, ConcurrentMap<TwoFaProviderType, TbRateLimits>> verificationCodeCheckingRateLimits = new ConcurrentHashMap<>();

    @Override
    public boolean isTwoFaEnabled(TenantId tenantId, UserId userId) {
        return configManager.getAccountTwoFaSettings(tenantId, userId)
                .map(settings -> !settings.getConfigs().isEmpty())
                .orElse(false);
    }

    @Override
    public void checkProvider(TenantId tenantId, TwoFaProviderType providerType) throws ThingsboardException {
        getTwoFaProvider(providerType).check(tenantId);
    }


    @Override
    public void prepareVerificationCode(SecurityUser user, TwoFaProviderType providerType, boolean checkLimits) throws Exception {
        TwoFaAccountConfig accountConfig = configManager.getTwoFaAccountConfig(user.getTenantId(), user.getId(), providerType)
                .orElseThrow(() -> ACCOUNT_NOT_CONFIGURED_ERROR);
        prepareVerificationCode(user, accountConfig, checkLimits);
    }

    @Override
    public void prepareVerificationCode(SecurityUser user, TwoFaAccountConfig accountConfig, boolean checkLimits) throws ThingsboardException {
        PlatformTwoFaSettings twoFaSettings = configManager.getPlatformTwoFaSettings(user.getTenantId(), true)
                .orElseThrow(() -> PROVIDER_NOT_CONFIGURED_ERROR);
        if (checkLimits) {
            Integer minVerificationCodeSendPeriod = twoFaSettings.getMinVerificationCodeSendPeriod();
            String rateLimit = null;
            if (minVerificationCodeSendPeriod != null && minVerificationCodeSendPeriod > 4) {
                rateLimit = "1:" + minVerificationCodeSendPeriod;
            }
            checkRateLimits(user.getId(), accountConfig.getProviderType(), rateLimit, verificationCodeSendingRateLimits);
        }

        TwoFaProviderConfig providerConfig = twoFaSettings.getProviderConfig(accountConfig.getProviderType())
                .orElseThrow(() -> PROVIDER_NOT_CONFIGURED_ERROR);
        getTwoFaProvider(accountConfig.getProviderType()).prepareVerificationCode(user, providerConfig, accountConfig);
    }


    @Override
    public boolean checkVerificationCode(SecurityUser user, TwoFaProviderType providerType, String verificationCode, boolean checkLimits) throws ThingsboardException {
        TwoFaAccountConfig accountConfig = configManager.getTwoFaAccountConfig(user.getTenantId(), user.getId(), providerType)
                .orElseThrow(() -> ACCOUNT_NOT_CONFIGURED_ERROR);
        return checkVerificationCode(user, verificationCode, accountConfig, checkLimits);
    }

    @Override
    public boolean checkVerificationCode(SecurityUser user, String verificationCode, TwoFaAccountConfig accountConfig, boolean checkLimits) throws ThingsboardException {
        if (!userService.findUserCredentialsByUserId(user.getTenantId(), user.getId()).isEnabled()) {
            throw new ThingsboardException("User is disabled", ThingsboardErrorCode.AUTHENTICATION);
        }

        PlatformTwoFaSettings twoFaSettings = configManager.getPlatformTwoFaSettings(user.getTenantId(), true)
                .orElseThrow(() -> PROVIDER_NOT_CONFIGURED_ERROR);
        if (checkLimits) {
            checkRateLimits(user.getId(), accountConfig.getProviderType(), twoFaSettings.getVerificationCodeCheckRateLimit(), verificationCodeCheckingRateLimits);
        }
        TwoFaProviderConfig providerConfig = twoFaSettings.getProviderConfig(accountConfig.getProviderType())
                .orElseThrow(() -> PROVIDER_NOT_CONFIGURED_ERROR);

        boolean verificationSuccess = false;
        if (StringUtils.isNotBlank(verificationCode)) {
            if (StringUtils.isNumeric(verificationCode) || accountConfig.getProviderType() == TwoFaProviderType.BACKUP_CODE) {
                verificationSuccess = getTwoFaProvider(accountConfig.getProviderType()).checkVerificationCode(user, verificationCode, providerConfig, accountConfig);
            }
        }
        if (checkLimits) {
            try {
                systemSecurityService.validateTwoFaVerification(user, verificationSuccess, twoFaSettings);
            } catch (LockedException e) {
                verificationCodeCheckingRateLimits.remove(user.getId());
                verificationCodeSendingRateLimits.remove(user.getId());
                throw new ThingsboardException(e.getMessage(), ThingsboardErrorCode.AUTHENTICATION);
            }
            if (verificationSuccess) {
                verificationCodeCheckingRateLimits.remove(user.getId());
                verificationCodeSendingRateLimits.remove(user.getId());
            }
        }
        return verificationSuccess;
    }

    private void checkRateLimits(UserId userId, TwoFaProviderType providerType, String rateLimitConfig,
                                 ConcurrentMap<UserId, ConcurrentMap<TwoFaProviderType, TbRateLimits>> rateLimits) throws ThingsboardException {
        if (StringUtils.isNotEmpty(rateLimitConfig)) {
            ConcurrentMap<TwoFaProviderType, TbRateLimits> providersRateLimits = rateLimits.computeIfAbsent(userId, i -> new ConcurrentHashMap<>());

            TbRateLimits rateLimit = providersRateLimits.get(providerType);
            if (rateLimit == null || !rateLimit.getConfiguration().equals(rateLimitConfig)) {
                rateLimit = new TbRateLimits(rateLimitConfig, true);
                providersRateLimits.put(providerType, rateLimit);
            }
            if (!rateLimit.tryConsume()) {
                throw new ThingsboardException("Too many requests", ThingsboardErrorCode.TOO_MANY_REQUESTS);
            }
        } else {
            rateLimits.remove(userId);
        }
    }


    @Override
    public TwoFaAccountConfig generateNewAccountConfig(User user, TwoFaProviderType providerType) throws ThingsboardException {
        TwoFaProviderConfig providerConfig = getTwoFaProviderConfig(user.getTenantId(), providerType);
        return getTwoFaProvider(providerType).generateNewAccountConfig(user, providerConfig);
    }


    private TwoFaProviderConfig getTwoFaProviderConfig(TenantId tenantId, TwoFaProviderType providerType) throws ThingsboardException {
        return configManager.getPlatformTwoFaSettings(tenantId, true)
                .flatMap(twoFaSettings -> twoFaSettings.getProviderConfig(providerType))
                .orElseThrow(() -> PROVIDER_NOT_CONFIGURED_ERROR);
    }

    private TwoFaProvider<TwoFaProviderConfig, TwoFaAccountConfig> getTwoFaProvider(TwoFaProviderType providerType) throws ThingsboardException {
        return Optional.ofNullable(providers.get(providerType))
                .orElseThrow(() -> PROVIDER_NOT_AVAILABLE_ERROR);
    }

    @Autowired
    private void setProviders(Collection<TwoFaProvider> providers) {
        providers.forEach(provider -> {
            this.providers.put(provider.getType(), provider);
        });
    }

}
