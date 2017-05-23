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
package fish.payara.notification.email;

import com.sun.enterprise.config.serverbeans.Config;
import fish.payara.nucleus.notification.BlockingQueueHandler;
import fish.payara.nucleus.notification.TestNotifier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.mail.Session;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.internal.api.Target;

/**
 *
 * @author jonathan coustick
 */
public class TestEmailNotifier extends TestNotifier {

    private static final String MESSAGE = "Email notifier test";
    
    @Param(name = "jndiName", optional = true)
     private String jndiName;
 
    @Param(name = "to", optional = true)
    private String to;

    @Inject
    EmailNotificationEventFactory factory;
    
    @Override
    public void execute(AdminCommandContext context) {
        
        /*ActionReport actionReport = context.getActionReport();
        
        Config config = targetUtil.getConfig(target);
        if (config == null) {
            context.getActionReport().setMessage("No such config named: " + target);
            context.getActionReport().setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
        }
        
        EmailNotifierConfiguration hipchatConfig = config.getExtensionByType(EmailNotifierConfiguration.class);
        
        if (jndiName == null){
                jndiName = hipchatConfig.getJndiName();
        }
        if (to == null){
            to = hipchatConfig.getTo();
        }
        //prepare hipchat message
        EmailNotificationEvent event = factory.buildNotificationEvent(SUBJECT, MESSAGE);
        
        EmailMessageQueue queue = new EmailMessageQueue();
        queue.addMessage(new EmailMessage(event, event.getSubject(), event.getMessage()));
        EmailNotifierConfigurationExecutionOptions options = new EmailNotifierConfigurationExecutionOptions();
        options.setJndiName(jndiName);
        options.setTo(to);
        
        EmailNotificationRunnable notifierRun = new EmailNotificationRunnable(queue,new Session(),  options);
        //set up logger to store result
        Logger logger = Logger.getLogger(EmailNotificationRunnable.class.getCanonicalName());
        BlockingQueueHandler bqh = new BlockingQueueHandler(10);
        bqh.setLevel(Level.FINE);
        Level oldLevel = logger.getLevel();
        logger.setLevel(Level.FINE);
        logger.addHandler(bqh);
        //send message, this occurs in its own thread
        Thread notifierThread = new Thread(notifierRun, "test-hipchat-notifier-thread");
        notifierThread.start();
        try {
            notifierThread.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(TestEmailNotifier.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            logger.setLevel(oldLevel);
        }
        LogRecord message = bqh.poll();
        bqh.clear();
        if (message == null){
            //something's gone wrong
            Logger.getLogger(TestEmailNotifier.class.getName()).log(Level.SEVERE, "Failed to send hipchat message");
            actionReport.setMessage("Failed to send hipchat message");
            actionReport.setActionExitCode(ActionReport.ExitCode.FAILURE);
        } else {;
            actionReport.setMessage(message.getMessage());
            if (message.getLevel()==Level.FINE){
                actionReport.setActionExitCode(ActionReport.ExitCode.SUCCESS);               
            } else {
                actionReport.setActionExitCode(ActionReport.ExitCode.FAILURE);
            }
            
            
        }*/
    }
}
