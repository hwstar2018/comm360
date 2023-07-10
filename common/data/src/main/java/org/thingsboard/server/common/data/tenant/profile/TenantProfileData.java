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
package org.thingsboard.server.common.data.tenant.profile;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@ApiModel
@Data
public class TenantProfileData {

    @ApiModelProperty(position = 1, value = "Complex JSON object that contains profile settings: max devices, max assets, rate limits, etc.")
    private TenantProfileConfiguration configuration;

    @ApiModelProperty(position = 2, value = "JSON array of queue configuration per tenant profile")
    private List<TenantProfileQueueConfiguration> queueConfiguration;

}
