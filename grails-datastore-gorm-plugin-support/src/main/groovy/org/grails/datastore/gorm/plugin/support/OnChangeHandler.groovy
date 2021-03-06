/* Copyright (C) 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.plugin.support

import java.lang.reflect.Method

import org.codehaus.groovy.grails.commons.GrailsServiceClass
import org.codehaus.groovy.grails.commons.ServiceArtefactHandler
import org.codehaus.groovy.grails.commons.spring.TypeSpecifyableTransactionProxyFactoryBean
import org.codehaus.groovy.grails.orm.support.GroovyAwareNamedTransactionAttributeSource
import org.codehaus.groovy.grails.plugins.GrailsPlugin
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.transaction.annotation.Transactional

/**
 * Common onChange handling logic.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class OnChangeHandler {

    abstract String getDatastoreType()

    void onChange(GrailsPlugin plugin, Map event) {
        if (!event.source || !event.ctx) {
            return
        }

        def application = event.application
        def serviceClass = application.addArtefact(ServiceArtefactHandler.TYPE, event.source)
        if (!shouldCreateTransactionalProxy(serviceClass)) {
            return
        }

        def beans = plugin.beans {
            def scope = serviceClass.getPropertyValue("scope")
            def props = ["*": "PROPAGATION_REQUIRED"] as Properties
            "${serviceClass.propertyName}"(TypeSpecifyableTransactionProxyFactoryBean, serviceClass.clazz) { bean ->
                if (scope) bean.scope = scope
                bean.lazyInit = true
                target = { innerBean ->
                    innerBean.factoryBean = "${serviceClass.fullName}ServiceClass"
                    innerBean.factoryMethod = "newInstance"
                    innerBean.autowire = "byName"
                    if (scope) innerBean.scope = scope
                }
                proxyTargetClass = true
                transactionAttributeSource = new GroovyAwareNamedTransactionAttributeSource(transactionalAttributes:props)
                transactionManager = ref("${datastoreType.toLowerCase()}TransactionManager")
            }
        }
        beans.registerBeans(event.ctx)
    }

    boolean shouldCreateTransactionalProxy(GrailsServiceClass serviceClass) {

         if (serviceClass.getStaticPropertyValue('transactional', Boolean)) {
             // leave it as a regular proxy
             return false
         }

         if (!datastoreType.equalsIgnoreCase(serviceClass.getStaticPropertyValue('transactional', String))) {
             return false
         }

         try {
             Class javaClass = serviceClass.clazz
             serviceClass.transactional &&
                 !AnnotationUtils.findAnnotation(javaClass, Transactional) &&
                 !javaClass.methods.any { Method m -> AnnotationUtils.findAnnotation(m, Transactional) != null }
         }
         catch (e) {
             return false
         }
     }
}
