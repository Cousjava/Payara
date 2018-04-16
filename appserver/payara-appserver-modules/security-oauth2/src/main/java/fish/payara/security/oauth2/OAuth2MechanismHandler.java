/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 *  Copyright (c) [2018] Payara Foundation and/or its affiliates. All rights reserved.
 * 
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common Development
 *  and Distribution License("CDDL") (collectively, the "License").  You
 *  may not use this file except in compliance with the License.  You can
 *  obtain a copy of the License at
 *  https://github.com/payara/Payara/blob/master/LICENSE.txt
 *  See the License for the specific
 *  language governing permissions and limitations under the License.
 * 
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License file at glassfish/legal/LICENSE.txt.
 * 
 *  GPL Classpath Exception:
 *  The Payara Foundation designates this particular file as subject to the "Classpath"
 *  exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *  file that accompanied this code.
 * 
 *  Modifications:
 *  If applicable, add the following below the License Header, with the fields
 *  enclosed by brackets [] replaced by your own identifying information:
 *  "Portions Copyright [year] [name of copyright owner]"
 * 
 *  Contributor(s):
 *  If you wish your version of this file to be governed by only the CDDL or
 *  only the GPL Version 2, indicate your decision by adding "[Contributor]
 *  elects to include this software in this distribution under the [CDDL or GPL
 *  Version 2] license."  If you don't indicate a single choice of license, a
 *  recipient has the option to distribute your version of this file under
 *  either the CDDL, the GPL Version 2 or to extend the choice of license to
 *  its licensees as provided above.  However, if you add GPL Version 2 code
 *  and therefore, elected the GPL Version 2 license, then the option applies
 *  only if the new code is made subject to such option by the copyright
 *  holder.
 */
package fish.payara.security.oauth2;

import java.util.logging.Level;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import fish.payara.security.oauth2.annotation.OAuth2AuthenticationDefinition;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.ProcessBean;
import javax.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import org.glassfish.soteria.cdi.CdiProducer;

/**
 * Handles the {@link OAuth2AuthenticationDefinition} annotation
 *
 * @author jonathan
 * @since 4.1.2.172
 */
public class OAuth2MechanismHandler implements Extension {

    private List<OAuth2AuthenticationDefinition> annotations;
    private Logger logger = Logger.getLogger("OAuthMechanism-Handler");

    public OAuth2MechanismHandler() {
        annotations = new ArrayList<>();
    }
    
    /**
     * This method tries to find the {@link OAuth2AuthenticationDefinition} annotation and if does flags that fact.
     * 
     * @param <T>
     * @param eventIn
     * @param beanManager
     */
    public <T> void findOAuth2DefinitionAnnotation(@Observes ProcessBean<T> eventIn, BeanManager beanManager) {
        
        //logger.log(Level.SEVERE, "OAuth2Handler Processing annotations..." + eventIn.toString());
        ProcessBean<T> event = eventIn; // JDK8 u60 workaround
      
        OAuth2AuthenticationDefinition annotation = event.getAnnotated().getAnnotation(OAuth2AuthenticationDefinition.class);
        if (annotation != null && !annotations.contains(annotation)) {
            logger.log(Level.SEVERE, "Processing annotation {0}", annotation);
            annotations.add(annotation);
        }
    }
    
    void beforeBeanDiscovery(@Observes BeforeBeanDiscovery event, BeanManager manager) {
        logger.log(Level.SEVERE, "OAuth2Handler - BeforeBeanDiscovery" + event.toString());
        event.addAnnotatedType(manager.createAnnotatedType(OAuth2AuthenticationMechanism.class), "OAuth2 Mechanism");
        
    }

    void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBean, BeanManager beanManager) {
        logger.log(Level.SEVERE, "Creating OAuth2 Mechanism");
        for (OAuth2AuthenticationDefinition annotation : annotations) {
//            afterBean.addBean(new OAuth2Producer(annotation)); // --> Bean is a POJO and does not have injection applied to it
//            
//            
//            afterBean.addBean()
//                    .types(OAuth2AuthenticationMechanism.class, HttpAuthenticationMechanism.class)
//                    .scope(ApplicationScoped.class)
//                    .addQualifier(Default.Literal.INSTANCE)
//                    .produceWith(obj -> new OAuth2AuthenticationMechanism(annotation)); // --> Bean is a POJO and does not have injection applied to it
//            
            
            afterBean.addBean(new CdiProducer<HttpAuthenticationMechanism>().
                    scope(ApplicationScoped.class)
                    .beanClass(HttpAuthenticationMechanism.class)
                    .types(HttpAuthenticationMechanism.class, Object.class)
                    .create(obj -> new OAuth2AuthenticationMechanism(annotation))); // --> Leads to NPE in CdiProducer.create(104)
            logger.log(Level.SEVERE, "OAuth2 Mechanism created successfully");

        }
        annotations.clear();
    }

}