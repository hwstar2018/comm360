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
package org.thingsboard.server.common.data;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.validation.NoXss;

/**
 * Created by igor on 2/27/18.
 */

@ApiModel
@AllArgsConstructor
public class ShortCustomerInfo {

    @ApiModelProperty(position = 1, value = "JSON object with the customer Id.")
    @Getter @Setter
    private CustomerId customerId;

    @ApiModelProperty(position = 2, value = "Title of the customer.")
    @Getter @Setter
    @NoXss
    private String title;

    @ApiModelProperty(position = 3, value = "Indicates special 'Public' customer used to embed dashboards on public websites.")
    @Getter @Setter
    private boolean isPublic;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ShortCustomerInfo that = (ShortCustomerInfo) o;

        return customerId.equals(that.customerId);

    }

    @Override
    public int hashCode() {
        return customerId.hashCode();
    }
}
