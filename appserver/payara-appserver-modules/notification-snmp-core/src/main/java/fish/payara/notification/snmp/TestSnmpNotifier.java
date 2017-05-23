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
package fish.payara.notification.snmp;

import com.sun.enterprise.config.serverbeans.Config;
import fish.payara.nucleus.notification.BlockingQueueHandler;
import fish.payara.nucleus.notification.TestNotifier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommandContext;

/**
 *
 * @author jonathan coustick
 */
public class TestSnmpNotifier extends TestNotifier {
    private static final String MESSAGE = "Snmp notifier test";
    
    @Param(name = "key", optional = true)
     private String key;
 
    @Param(name = "accountId", optional = true)
    private String accountId;

    @Inject
    SnmpNotificationEventFactory factory;
    
    @Override
    public void execute(AdminCommandContext context) {
        
        ActionReport actionReport = context.getActionReport();
        
        Config config = targetUtil.getConfig(target);
        if (config == null) {
            context.getActionReport().setMessage("No such config named: " + target);
            context.getActionReport().setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
        }
        
        SnmpNotifierConfiguration hipchatConfig = config.getExtensionByType(SnmpNotifierConfiguration.class);
        
        if (key == null){
                key = hipchatConfig.getKey();
        }
        if (accountId == null){
            accountId = hipchatConfig.getAccountId();
        }
        //prepare hipchat message
        SnmpNotificationEvent event = factory.buildNotificationEvent(SUBJECT, MESSAGE);
        
        SnmpMessageQueue queue = new SnmpMessageQueue();
        queue.addMessage(new SnmpMessage(event, event.getSubject(), event.getMessage()));
        SnmpNotifierConfigurationExecutionOptions options = new SnmpNotifierConfigurationExecutionOptions();
        options.setKey(key);
        options.setAccountId(accountId);
        
        SnmpNotificationRunnable notifierRun = new SnmpNotificationRunnable(queue, options);
        //set up logger to store result
        Logger logger = Logger.getLogger(SnmpNotificationRunnable.class.getCanonicalName());
        BlockingQueueHandler bqh = new BlockingQueueHandler(10);
        bqh.setLevel(Level.FINE);
        Level oldLevel = logger.getLevel();
        logger.setLevel(Level.FINE);
        logger.addHandler(bqh);
        //send message, this occurs in its own thread
        Thread notifierThread = new Thread(notifierRun, "test-newrelic-notifier-thread");
        notifierThread.start();
        try {
            notifierThread.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(TestSnmpNotifier.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            logger.setLevel(oldLevel);
        }
        LogRecord message = bqh.poll();
        bqh.clear();
        if (message == null){
            //something's gone wrong
            Logger.getLogger(TestSnmpNotifier.class.getName()).log(Level.SEVERE, "Failed to send SNMP message");
            actionReport.setMessage("Failed to send SNMP message");
            actionReport.setActionExitCode(ActionReport.ExitCode.FAILURE);
        } else {
            actionReport.setMessage(message.getMessage());
            if (message.getLevel()==Level.FINE){
                actionReport.setActionExitCode(ActionReport.ExitCode.SUCCESS);               
            } else {
                actionReport.setActionExitCode(ActionReport.ExitCode.FAILURE);
            }
            
            
        }
        
    }
}
