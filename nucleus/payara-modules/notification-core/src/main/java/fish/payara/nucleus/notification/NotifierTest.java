/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 *    Copyright (c) [2017] Payara Foundation and/or its affiliates. All rights reserved.
 * 
 *     The contents of this file are subject to the terms of either the GNU
 *     General Public License Version 2 only ("GPL") or the Common Development
 *     and Distribution License("CDDL") (collectively, the "License").  You
 *     may not use this file except in compliance with the License.  You can
 *     obtain a copy of the License at
 *     https://github.com/payara/Payara/blob/master/LICENSE.txt
 *     See the License for the specific
 *     language governing permissions and limitations under the License.
 * 
 *     When distributing the software, include this License Header Notice in each
 *     file and include the License file at glassfish/legal/LICENSE.txt.
 * 
 *     GPL Classpath Exception:
 *     The Payara Foundation designates this particular file as subject to the "Classpath"
 *     exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *     file that accompanied this code.
 * 
 *     Modifications:
 *     If applicable, add the following below the License Header, with the fields
 *     enclosed by brackets [] replaced by your own identifying information:
 *     "Portions Copyright [year] [name of copyright owner]"
 * 
 *     Contributor(s):
 *     If you wish your version of this file to be governed by only the CDDL or
 *     only the GPL Version 2, indicate your decision by adding "[Contributor]
 *     elects to include this software in this distribution under the [CDDL or GPL
 *     Version 2] license."  If you don't indicate a single choice of license, a
 *     recipient has the option to distribute your version of this file under
 *     either the CDDL, the GPL Version 2 or to extend the choice of license to
 *     its licensees as provided above.  However, if you add GPL Version 2 code
 *     and therefore, elected the GPL Version 2 license, then the option applies
 *     only if the new code is made subject to such option by the copyright
 *     holder.
 */
package fish.payara.nucleus.notification;

import com.sun.enterprise.security.SecurityServicesUtil;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import fish.payara.nucleus.notification.domain.NotificationEvent;
import fish.payara.nucleus.notification.service.BaseNotifierService;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.internal.api.Globals;
import org.glassfish.internal.api.ServerContext;

/**
 * Superclass for testing of notifiers. This is not part of the test suite, and
 * is instead used after configuring a notifier to make sure that it is working
 * and will be used by end users.
 * @author jonathan coustick
 */
public abstract class NotifierTest {
    
    protected ServiceLocator habitat;
    protected ServerContext serverctx;
    
    protected static final String SUBJECT = "Notifier Test";
    
    public NotifierTest(){
        habitat = Globals.getDefaultHabitat();
        serverctx = habitat.getService(ServerContext.class);
    }
    /**
     * Tests the notifier
     * @return the LogRecord if there is an error, null otherwise
     */
    public abstract LogRecord testNotifier();
    
    protected synchronized LogRecord processEvent(NotificationEvent event, Logger logger, BaseNotifierService service ){
        BlockingQueueHandler bqh = new BlockingQueueHandler();
        bqh.setLevel(Level.FINER);
        logger.addHandler(bqh);
        service.handleNotification(event);
        try {
            //Thread.currentThread().
            wait(500);
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            Logger.getLogger(NotifierTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        LogRecord record = bqh.poll();
        logger.removeHandler(bqh);
        return record;   
    }
    
}
