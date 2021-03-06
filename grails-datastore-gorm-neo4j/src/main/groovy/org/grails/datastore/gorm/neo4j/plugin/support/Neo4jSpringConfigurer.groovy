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
package org.grails.datastore.gorm.neo4j.plugin.support

import org.grails.datastore.gorm.plugin.support.SpringConfigurer
import org.grails.datastore.gorm.neo4j.bean.factory.Neo4jMappingContextFactoryBean
import org.grails.datastore.gorm.neo4j.bean.factory.Neo4jDatastoreFactoryBean
import org.springframework.util.Assert
import org.grails.datastore.gorm.neo4j.Neo4jOpenSessionInViewInterceptor
import org.neo4j.kernel.impl.transaction.SpringTransactionManager
import org.neo4j.kernel.impl.transaction.UserTransactionImpl
import org.springframework.transaction.jta.JtaTransactionManager

/**
 * Spring configurer for Neo4j
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class Neo4jSpringConfigurer extends SpringConfigurer {
    @Override
    String getDatastoreType() {
        return "Neo4j"
    }

    @Override
    Closure getSpringCustomizer() {
        return {
           def neo4jConfig = application.config?.grails?.neo4j  // use config from app's Datasource.groovy

            String neo4jGraphDatabaseClassName
            String neo4jDefaultLocation
            switch (neo4jConfig.type) {
                case "rest":
                    neo4jGraphDatabaseClassName = "org.neo4j.rest.graphdb.RestGraphDatabase"
                    neo4jDefaultLocation = "http://localhost:7474/db/data/"
                    break
                case "ha":
                    neo4jGraphDatabaseClassName = "org.neo4j.kernel.HighlyAvailableGraphDatabase"
                    neo4jDefaultLocation = "data/neo4j"
                    break
                case "embedded":
                    neo4jGraphDatabaseClassName = "org.neo4j.kernel.EmbeddedGraphDatabase"
                    neo4jDefaultLocation = "data/neo4j"
                    break
                default:  // otherwise type is used as classname
                    neo4jGraphDatabaseClassName = neo4jConfig.type
                    neo4jDefaultLocation = "data/neo4j"
                    break
            }

            Class neo4jGraphDatabaseClass
            try {
                neo4jGraphDatabaseClass = neo4jGraphDatabaseClassName as Class
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("could not load $neo4jGraphDatabaseClassName, maybe add neo4j-enterprise to dependecies section", e)
            }

            graphDatabaseService(
                     neo4jGraphDatabaseClass,
                     neo4jConfig.location ?: neo4jDefaultLocation,
                     neo4jConfig.params ?: [:]

            ) { bean ->
                bean.destroyMethod = "shutdown"
            }

            neo4jMappingContext(Neo4jMappingContextFactoryBean) {
                grailsApplication = ref('grailsApplication')
                pluginManager = ref('pluginManager')
            }

            neo4jDatastore(Neo4jDatastoreFactoryBean) {
                graphDatabaseService = graphDatabaseService
                mappingContext = neo4jMappingContext

            }

            neo4jOpenSessionInViewInterceptor(Neo4jOpenSessionInViewInterceptor) {
                datastore = ref("neo4jDatastore")
            }

            neo4jTransactionManagerService(SpringTransactionManager, graphDatabaseService)
            neo4jUserTransactionService(UserTransactionImpl, graphDatabaseService)
            neo4jTransactionManager(JtaTransactionManager) {
                transactionManager = neo4jTransactionManagerService
                userTransaction = neo4jUserTransactionService
            }

///*
//        indexService(LuceneFulltextQueryIndexService, ref("graphDatabaseService")) { bean ->
//        //indexService(LuceneFulltextIndexService, ref("graphDatabaseService")) { bean ->
//            bean.destroyMethod = "shutdown"
//        }
//*/
//
//        if (manager?.hasGrailsPlugin("controllers")) {
//            neo4jOpenSessionInViewInterceptor(Neo4jOpenSessionInViewInterceptor) {
//                datastore = ref("neo4jDatastore")
//            }
//            if (getSpringConfig().containsBean("controllerHandlerMappings")) {
//                controllerHandlerMappings.interceptors << neo4jOpenSessionInViewInterceptor
//            }
//            if (getSpringConfig().containsBean("annotationHandlerMapping")) {
//                if (annotationHandlerMapping.interceptors) {
//                    annotationHandlerMapping.interceptors << neo4jOpenSessionInViewInterceptor
//                }
//                else {
//                    annotationHandlerMapping.interceptors = [neo4jOpenSessionInViewInterceptor]
//                }
//            }
//        }
//
        }
    }
}
