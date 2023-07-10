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
package org.thingsboard.server.common.data.event;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.thingsboard.server.common.data.StringUtils;

@Data
@ApiModel
public class ErrorEventFilter implements EventFilter {

    @ApiModelProperty(position = 1, value = "String value representing the server name, identifier or ip address where the platform is running", example = "ip-172-31-24-152")
    protected String server;
    @ApiModelProperty(position = 2, value = "String value representing the method name when the error happened", example = "onClusterEventMsg")
    protected String method;
    @ApiModelProperty(position = 3, value = "The case insensitive 'contains' filter based on error message", example = "not present in the DB")
    protected String errorStr;

    @Override
    public EventType getEventType() {
        return EventType.ERROR;
    }

    @Override
    public boolean isNotEmpty() {
        return !StringUtils.isEmpty(server) || !StringUtils.isEmpty(method) || !StringUtils.isEmpty(errorStr);
    }
}
