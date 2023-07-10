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
package org.thingsboard.server.transport.coap;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;

import java.util.concurrent.CountDownLatch;

@Slf4j
@Data
public class CoapTestCallback implements CoapHandler {

    protected final CountDownLatch latch;
    protected Integer observe;
    protected byte[] payloadBytes;
    protected CoAP.ResponseCode responseCode;

    public CoapTestCallback() {
        this.latch = new CountDownLatch(1);
    }

    public CoapTestCallback(int subscribeCount) {
        this.latch = new CountDownLatch(subscribeCount);
    }

    public Integer getObserve() {
        return observe;
    }

    public byte[] getPayloadBytes() {
        return payloadBytes;
    }

    public CoAP.ResponseCode getResponseCode() {
        return responseCode;
    }

    @Override
    public void onLoad(CoapResponse response) {
        observe = response.getOptions().getObserve();
        payloadBytes = response.getPayload();
        responseCode = response.getCode();
        latch.countDown();
    }

    @Override
    public void onError() {
        log.warn("Command Response Ack Error, No connect");
    }

}
