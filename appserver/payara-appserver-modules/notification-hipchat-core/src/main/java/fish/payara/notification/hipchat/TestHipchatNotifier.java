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
package fish.payara.notification.hipchat;

import fish.payara.nucleus.notification.TestNotifier;
import fish.payara.nucleus.notification.configuration.HipchatNotifier;
import fish.payara.nucleus.notification.configuration.NotificationServiceConfiguration;
import fish.payara.nucleus.notification.service.NotificationRunnable;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.logging.Level;
import javax.inject.Inject;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandLock;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

/**
 *
 * @author jonathan coustick
 */
@Service(name = "test-hipchat-notifier-configuration")
@PerLookup
@CommandLock(CommandLock.LockType.NONE)
@ExecuteOn({RuntimeType.DAS, RuntimeType.INSTANCE})
@TargetType(value = {CommandTarget.DAS, CommandTarget.STANDALONE_INSTANCE, CommandTarget.CLUSTER, CommandTarget.CLUSTERED_INSTANCE, CommandTarget.CONFIG})
@RestEndpoints({
        @RestEndpoint(configBean = NotificationServiceConfiguration.class,
                opType = RestEndpoint.OpType.GET,
                path = "test-hipchat-notifier-configuration",
                description = "Tests Hipchat Notifier Configuration")
})
public class TestHipchatNotifier extends TestNotifier {
 
    private static final String MESSAGE = "Hipchat Notifier Test";
    
    @Param(name = "roomName", optional = true)
    private String roomName;

    @Param(name = "token", optional = true)
    private String token;

    @Inject
    private HipchatNotifier hipchatNotifier;
    
    @Inject
    private HipchatNotifierConfiguration hipchatConfig;
    
    @Inject
    private HipchatNotificationEventFactory messageFactory;
    
    @Override
    public void execute(AdminCommandContext context) {
        try {
            String enabled = hipchatNotifier.getEnabled();
            //Enable notifier tempoarally
            if (enabled == "false") {
                hipchatNotifier.enabled(Boolean.TRUE);
            }
            
            if (roomName == null){
                roomName = hipchatConfig.getRoomName();
            }
            if (token == null){
                token = hipchatConfig.getToken();
            }

            HipchatNotificationEvent event = messageFactory.buildNotificationEvent(SUBJECT, MESSAGE);
            
            String formattedURL = MessageFormat.format(HIPCHAT_RESOURCE, roomName, token);
            String fullURL = HIPCHAT_ENDPOINT + formattedURL;
            try {
                URL url = new URL(fullURL);
                HttpURLConnection connection = createConnection(url,
                        new NotificationRunnable.Header(HEADER_CONTENTTYPE, ACCEPT_TYPE_TEXT_PLAIN));

                HipchatMessage message = queue.getMessage();
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write((message.getSubject() + "\n" + message.getMessage()).getBytes(Charsets.UTF8_CHARSET));
                    if (connection.getResponseCode() != 204) {
                        logger.log(Level.SEVERE,
                                "Error occurred while connecting Hipchat. Check your room name and token. HTTP response code",
                                connection.getResponseCode());
                    }
                }
            }
            catch (MalformedURLException e) {
                logger.log(Level.SEVERE, "Error occurred while accessing URL: " + fullURL, e);
            }
            catch (ProtocolException e) {
                logger.log(Level.SEVERE, "Specified URL is not accepting protocol defined: " + HTTP_METHOD_POST, e);
            }
            catch (UnknownHostException e) {
                logger.log(Level.SEVERE, "Check your network connection. Cannot access URL: " + fullURL, e);
            }
            catch (ConnectException e) {
                logger.log(Level.SEVERE, "Error occurred while connecting URL: " + fullURL, e);
            }
            catch (IOException e) {
                logger.log(Level.SEVERE, "IO Error while accessing URL: " + fullURL, e);
            }
            
            //If notifier was disabled at start then re-disable it
            if (enabled == "false") {
                hipchatNotifier.enabled(Boolean.FALSE);
            }
        } catch (PropertyVetoException e) {
        
        
        }
    }
    
}
