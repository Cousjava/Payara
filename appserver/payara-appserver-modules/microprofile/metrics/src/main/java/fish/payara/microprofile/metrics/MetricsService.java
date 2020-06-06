/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 *    Copyright (c) [2018-2020] Payara Foundation and/or its affiliates. All rights reserved.
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
package fish.payara.microprofile.metrics;

import fish.payara.microprofile.metrics.admin.MetricsServiceConfiguration;
import fish.payara.microprofile.metrics.cdi.MetricsHelper;
import fish.payara.microprofile.metrics.exception.NoSuchMetricException;
import fish.payara.microprofile.metrics.exception.NoSuchRegistryException;
import fish.payara.microprofile.metrics.impl.MetricRegistryImpl;
import fish.payara.microprofile.metrics.jmx.MBeanMetadata;
import fish.payara.microprofile.metrics.jmx.MBeanMetadataConfig;
import fish.payara.microprofile.metrics.jmx.MBeanMetadataHelper;
import fish.payara.monitoring.collect.MonitoringDataCollector;
import fish.payara.monitoring.collect.MonitoringDataSource;
import fish.payara.nucleus.executorservice.PayaraExecutorService;
import java.beans.PropertyChangeEvent;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.glassfish.api.StartupRunLevel;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.api.event.EventListener;
import org.glassfish.api.event.Events;
import org.glassfish.api.invocation.InvocationManager;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.internal.api.Globals;
import org.glassfish.internal.data.ApplicationInfo;
import org.glassfish.internal.deployment.Deployment;
import org.jvnet.hk2.annotations.Service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.xml.bind.JAXB;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToLongFunction;
import java.util.logging.Logger;
import org.eclipse.microprofile.metrics.MetricID;

import static org.eclipse.microprofile.metrics.MetricRegistry.Type.BASE;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type.VENDOR;
import org.glassfish.internal.data.ApplicationRegistry;
import org.jvnet.hk2.config.ConfigListener;
import org.jvnet.hk2.config.UnprocessedChangeEvent;
import org.jvnet.hk2.config.UnprocessedChangeEvents;

@Service(name = "microprofile-metrics-service")
@RunLevel(StartupRunLevel.VAL)
public class MetricsService implements EventListener, ConfigListener, MonitoringDataSource {

    private static final Logger LOGGER = Logger.getLogger(MetricsService.class.getName());

    @Inject
    Events events;

    @Inject
    ApplicationRegistry applicationRegistry;

    @Inject
    MetricsServiceConfiguration configuration;
    
    @Inject
    private ServerEnvironment serverEnv;

    @Inject
    ServiceLocator serviceLocator;

    @Inject
    private MBeanMetadataHelper helper;

    private MetricsServiceConfiguration metricsServiceConfiguration;

    private Boolean metricsEnabled;

    private Boolean metricsSecure;

    private List<MBeanMetadata> unresolvedBaseMetadataList;

    private List<MBeanMetadata> unresolvedVendorMetadataList;

    private final Map<String, MetricRegistryImpl> REGISTRIES = new ConcurrentHashMap<>();//stores registries of base, vendor, app1, app2, ... app(n) etc

    public MetricsService() {

    }

    @PostConstruct
    public void init() {
        if(events == null){
            events = Globals.getDefaultBaseServiceLocator().getService(Events.class);
        }
        events.register(this);
        metricsServiceConfiguration = serviceLocator.getService(MetricsServiceConfiguration.class);
        // Only start if metrics are enabled
        if (isEnabled()) {
            PayaraExecutorService payaraExecutor = serviceLocator.getService(PayaraExecutorService.class, new Annotation[0]);
            payaraExecutor.submit(() -> {
                bootstrap();
            });
        }
    }

    @Override
    public void collect(MonitoringDataCollector rootCollector) {
        if (!isEnabled())
            return;
        MonitoringDataCollector metricsCollector = rootCollector.in("metric");
        for (Entry<String, ? extends MetricRegistry> registry : REGISTRIES.entrySet()) {
            collectRegistry(registry, metricsCollector);
        }
    }

    private static void collectRegistry(Entry<String, ? extends MetricRegistry> registry, MonitoringDataCollector collector) {
        for (Entry<MetricID, Gauge> gauge : registry.getValue().getGauges().entrySet()) {
            Object value = gauge.getValue().getValue();
            if (value instanceof Number) {
                tagCollector(gauge.getKey(), collector).collect(toName(gauge.getKey()), ((Number) value));
            }
        }
        collectMetrics(registry.getValue().getCounters(), counter -> counter.getCount(), collector);
        collectMetrics(registry.getValue().getConcurrentGauges(), gauge -> gauge.getCount(), collector);
        collectMetrics(registry.getValue().getHistograms(), histogram -> histogram.getCount(), collector);
        collectMetrics(registry.getValue().getMeters(), meter -> meter.getCount(), collector);
        collectMetrics(registry.getValue().getTimers(), timer -> timer.getCount(), collector);
    }

    private static <T extends Metric> void collectMetrics(Map<MetricID, T> metrics, ToLongFunction<T> count, 
            MonitoringDataCollector collector) {
        for (Entry<MetricID, T> metric : metrics.entrySet()) {
            tagCollector(metric.getKey(), collector).collect(toName(metric.getKey()), count.applyAsLong(metric.getValue()));
        }
    }

    private static CharSequence toName(MetricID metric) {
        String name = metric.getName();
        return name.indexOf(' ') < 0 ? name : name.replace(' ', '.'); // trying to avoid replace
    }

    private static MonitoringDataCollector tagCollector(MetricID metric, MonitoringDataCollector collector) {
        Map<String, String> tags = metric.getTags();
        if (tags.isEmpty()) {
            return collector;
        }
        StringBuilder tag = new StringBuilder();
        for (Entry<String, String> e : metric.getTags().entrySet()) {
            if (tag.length() > 0) {
                tag.append('_');
            }
            if (!"name".equals(e.getKey())) {
                tag.append(e.getKey().replace(' ', '.'));
            }
            tag.append(e.getValue().replace(' ', '.'));
        }
        return collector.group(tag);
    }

    private void checkSystemCpuLoadIssue(MBeanMetadataConfig metadataConfig) {
        // Could be constant but placed it in method as it is a workaround until fixed in JVM.
        // TODO Make this check dependent on the JDK version (as it hopefully will get solved in the future) -> Azul fix request made.
        String mbeanSystemCPULoad = "java.lang:type=OperatingSystem/SystemCpuLoad";
        long count = metadataConfig.getBaseMetadata().stream()
                .map(MBeanMetadata::getMBean)
                .filter(mbeanSystemCPULoad::equalsIgnoreCase)
                .count();

        count += metadataConfig.getVendorMetadata().stream()
                .map(MBeanMetadata::getMBean)
                .filter(mbeanSystemCPULoad::equalsIgnoreCase)
                .count();

        if (count > 1) {
            LOGGER.warning(String.format("Referencing the MBean value %s multiple times possibly leads to inconsistent values for the MBean value.", mbeanSystemCPULoad));
        }
    }

    @Override
    public void event(Event event) {
        if (event.is(Deployment.APPLICATION_LOADED)) {
            ApplicationInfo info = (ApplicationInfo) event.hook();
            registerApplication(info.getName());
        } else if (event.is(Deployment.APPLICATION_UNLOADED)) {
            ApplicationInfo info = (ApplicationInfo) event.hook();
            deregisterApplication(info.getName());
        }
    }

    /**
     * Initialize metrics from the metrics.xml containing the base & vendor
     * metrics metadata.
     *
     * @param metadataConfig
     */
    private void initMetadataConfig(List<MBeanMetadata> baseMetadataList, List<MBeanMetadata> vendorMetadataList, boolean isRetry) {
        if (!baseMetadataList.isEmpty()) {
            unresolvedBaseMetadataList = helper.registerMetadata(
                    getOrAddRegistry(BASE.getName()),
                    baseMetadataList,
                    isRetry);
        }
        if (!vendorMetadataList.isEmpty()) {
            unresolvedVendorMetadataList = helper.registerMetadata(
                    getOrAddRegistry(VENDOR.getName()),
                    vendorMetadataList,
                    isRetry);
        }
    }

    /**
     * Registers unresolved MBeans if they have been started after the metrics
     * service.
     */
    public void reregisterMetadataConfig() {
        // Initialise the metadata lists if they haven't yet
        if (unresolvedBaseMetadataList == null || unresolvedVendorMetadataList == null) {
            bootstrap();
        } else {
            initMetadataConfig(unresolvedBaseMetadataList, unresolvedVendorMetadataList, true);
        }
    }

    private MBeanMetadataConfig getConfig() {   
        InputStream defaultConfig = MetricsHelper.class.getResourceAsStream("/metrics.xml");
        MBeanMetadataConfig config = JAXB.unmarshal(defaultConfig, MBeanMetadataConfig.class);
          
        File metricsResource = new File(serverEnv.getConfigDirPath(), "metrics.xml");
        if (metricsResource.exists()) {
            try {
                InputStream userMetrics = new FileInputStream(metricsResource);
                MBeanMetadataConfig extraConfig = JAXB.unmarshal(userMetrics, MBeanMetadataConfig.class);
                config.addBaseMetadata(extraConfig.getBaseMetadata());
                config.addVendorMetadata(extraConfig.getVendorMetadata());
                
            } catch (FileNotFoundException ex) {
                //ignore
            }
        }
        return config;
    }

    public Boolean isEnabled() {
        if (metricsEnabled == null) {
            metricsEnabled = Boolean.valueOf(metricsServiceConfiguration.getEnabled());
        }
        return metricsEnabled;
    }

    public void resetMetricsEnabledProperty() {
        metricsEnabled = null;
    }

    public Boolean isMetricsSecure() {
        if (metricsSecure == null) {
            metricsSecure = Boolean.valueOf(metricsServiceConfiguration.getSecureMetrics());
        }
        return metricsSecure;
    }

    public void resetMetricsSecureProperty() {
        metricsSecure = null;
    }

    public boolean isSecurityEnabled() {
        return Boolean.parseBoolean(metricsServiceConfiguration.getSecurityEnabled());
    }

    public <T extends Metric> T getApplicationMetric(MetricID metricID, Class<T> type) throws NoSuchRegistryException {
        return getRegistryInternal(getApplicationName()).getMetric(metricID, type);
    }

    public Map<MetricID, Metric> getMetricsAsMap(String registryName) throws NoSuchRegistryException {
        return getRegistry(registryName).getMetrics();
    }

    public Map<String, Metadata> getMetadataAsMap(String registryName) throws NoSuchRegistryException {
        return getRegistry(registryName).getMetadata();
    }

    public Set<MetricID> getMetricsIDs(String registryName, String metricName) throws NoSuchRegistryException {
        return getRegistryInternal(registryName).getMetricsIDs(metricName);
    }

    public Map<MetricID, Metric> getMetricsAsMap(String registryName, String metricName) throws NoSuchRegistryException {
        return getRegistryInternal(registryName).getMetrics(metricName);
    }

    public Map<String, Metadata> getMetadataAsMap(String registryName, String metricName) throws NoSuchRegistryException, NoSuchMetricException {
        Metadata metadata = getRegistryInternal(registryName).getMetadata(metricName);
        if (metadata != null) {
            return Collections.singletonMap(metricName, metadata);
        }
        throw new NoSuchMetricException(metricName);
    }

    /**
     * Returns the Metrics registry based on respective registry name
     * 
     * @param registryName
     * @return 
     * @throws fish.payara.microprofile.metrics.exception.NoSuchRegistryException 
     */ 
    public MetricRegistry getRegistry(String registryName) throws NoSuchRegistryException {
        return getRegistryInternal(registryName);
    }

    private MetricRegistryImpl getRegistryInternal(String registryName) throws NoSuchRegistryException {
        MetricRegistryImpl registry = REGISTRIES.get(registryName.toLowerCase());
        if (registry == null) {
            throw new NoSuchRegistryException(registryName);
        }
        return registry;
    }

    public Set<String> getApplicationRegistryNames() {
        Set<String> applicationRegistries = new HashSet<>(REGISTRIES.keySet());
        applicationRegistries.remove(BASE.getName());
        applicationRegistries.remove(VENDOR.getName());
        return applicationRegistries;
    }
    
    public Set<String> getAllRegistryNames() {
        return REGISTRIES.keySet();
    }
        
    /**
     * Returns the Metrics registry based on respective registry name, 
     * if not available then add the new MetricRegistry instance
     * 
     * @param registryName
     * @return 
     */    
    public MetricRegistry getOrAddRegistry(String registryName) {
        return REGISTRIES.computeIfAbsent(registryName.toLowerCase(), key -> new MetricRegistryImpl());
    }
    
    public MetricRegistry getApplicationRegistry() {
        return getOrAddRegistry(getApplicationName());
    }

    /**
     * Remove the Metrics registry
     * 
     * @param registryName
     * @return 
     */
    public MetricRegistry removeRegistry(String registryName) {
        return REGISTRIES.remove(registryName.toLowerCase());
    }
    
    /**
     * Adds an application to the enabled map
     *
     * @param applicationName The name of the application to remove
     */
    private void registerApplication(String applicationName) {
        getOrAddRegistry(applicationName);
    }

    /**
     * Removes an application from the enabled map
     *
     * @param applicationName The name of the application to remove
     */
    private void deregisterApplication(String applicationName) {
        removeRegistry(applicationName);
    }

    /**
     * Gets the application name from the invocation manager.
     *
     * @return The application name
     */
    public String getApplicationName() {
        InvocationManager invocationManager = Globals.getDefaultBaseServiceLocator()
                .getService(InvocationManager.class);
        if (invocationManager.getCurrentInvocation() == null) {
            return invocationManager.peekAppEnvironment().getName();
        }
        String appName = invocationManager.getCurrentInvocation().getAppName();
        if (appName == null) {
            appName = invocationManager.getCurrentInvocation().getModuleName();
        }
        if (appName == null) {
            appName = invocationManager.getCurrentInvocation().getComponentId();
        }
        return appName;
    }

    private void bootstrap() {
        MBeanMetadataConfig metadataConfig = getConfig();
        checkSystemCpuLoadIssue(metadataConfig); // PAYARA 2938
        initMetadataConfig(metadataConfig.getBaseMetadata(), metadataConfig.getVendorMetadata(), false);
    }

    @Override
    public UnprocessedChangeEvents changed(PropertyChangeEvent[] events) {
        List<UnprocessedChangeEvent> unchangedList = new ArrayList<>();
        for(PropertyChangeEvent event : events) {
                unchangedList.add(new UnprocessedChangeEvent(event, "Microprofile Metrics configuration changed:" + event.getPropertyName()
                        + " was changed from " + event.getOldValue().toString() + " to " + event.getNewValue().toString()));
            }
        return new UnprocessedChangeEvents(unchangedList);
    }
}
