/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2008-2020 the original author or authors.
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
package griffon.builder.lanterna.factory

import com.googlecode.lanterna.gui.component.Panel
import griffon.builder.lanterna.LanternaBuilderCustomizer
import griffon.util.groovy.BuilderCustomizer
import griffon.util.groovy.CompositeBuilder
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class BoxFactorySpec extends Specification {
    void "Can create an horizontal Box with title '#value'"() {
        given:
        CompositeBuilder builder = new CompositeBuilder([new LanternaBuilderCustomizer()] as BuilderCustomizer[])

        when:
        Panel box = builder.hbox(value)

        then:
        box != null
        box.title == title

        where:
        value      | title
        null       | ''
        'lanterna' | 'lanterna'
    }

    void "Can create a vertical Box with title '#value'"() {
        given:
        CompositeBuilder builder = new CompositeBuilder([new LanternaBuilderCustomizer()] as BuilderCustomizer[])

        when:
        Panel box = builder.vbox(value)

        then:
        box != null
        box.title == title

        where:
        value      | title
        null       | ''
        'lanterna' | 'lanterna'
    }
}
