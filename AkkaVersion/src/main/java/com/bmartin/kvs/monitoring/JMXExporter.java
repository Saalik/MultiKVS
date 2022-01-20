package com.bmartin.kvs.monitoring;

import akka.http.javadsl.model.HttpRequest;
import io.prometheus.jmx.shaded.io.prometheus.client.Collector;
import io.prometheus.jmx.shaded.io.prometheus.client.CollectorRegistry;
import io.prometheus.jmx.shaded.io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.jmx.shaded.io.prometheus.jmx.JmxCollector;
import org.slf4j.Logger;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class JMXExporter {
    Logger log;
    CollectorRegistry registry;
    //    BuildInfoCollector buildInfoCollector;
    JmxCollector collector;

    public JMXExporter(Logger log) {
        assert (null != log);
        this.log = log;
        try {
            InputStream configStream = getClass().getClassLoader().getResourceAsStream("jmx_exporter.yaml");

            this.registry = CollectorRegistry.defaultRegistry;
//            this.buildInfoCollector = new BuildInfoCollector().register(this.registry);
            this.collector = new JmxCollector(configStream).register();

            DefaultExports.initialize();

            register(0);

        } catch (MalformedObjectNameException | IllegalArgumentException e) {
            e.printStackTrace();
            // TODO: ?
        }
    }

    void register(int retry) {
        if (retry == 5) {
            log.info("Could not find MonitoringActor MXBean. This might be normal as MonitoringActor is a Cluster singleton.");
            return;
        }

        try {
            // wait for MonitoringActor to be started and MXBean to be registered
            Thread.sleep(1000);
            (new NodeGraphCollector()).register(registry);
            log.info("Successfully registered Prometheus NodeGraphCollector");
        } catch (IllegalArgumentException | InterruptedException | MalformedObjectNameException | InstanceNotFoundException e) {
            log.debug("register NodeGraphCollector retry=" + retry);
            register(retry + 1);
        }
    }

    public String getJmxMetrics(HttpRequest r) {
        try {
            String rawQuery = r.getUri().rawQueryString().toString();
            Enumeration<Collector.MetricFamilySamples> res = registry.filteredMetricFamilySamples(parseQuery(rawQuery));

            return write004(res);
        } catch (IOException e) {
            e.printStackTrace();
            return "error while collecting JMX info";
        }
    }

    private static Set<String> parseQuery(String query) throws IOException {
        Set<String> names = new HashSet<>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx != -1 && URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8).equals("name[]")) {
                    names.add(URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
                }
            }
        }

        return names;
    }

    public static String write004(Enumeration<Collector.MetricFamilySamples> mfs) throws IOException {
        StringBuilder res = new StringBuilder();
        Map<String, Collector.MetricFamilySamples> omFamilies = new TreeMap<>();
        /* See http://prometheus.io/docs/instrumenting/exposition_formats/
         * for the output format specification. */
        while (mfs.hasMoreElements()) {
            Collector.MetricFamilySamples metricFamilySamples = mfs.nextElement();
            String name = metricFamilySamples.name;
            res.append("# HELP ");
            res.append(name);
            if (metricFamilySamples.type == Collector.Type.COUNTER) {
                res.append("_total");
            }
            if (metricFamilySamples.type == Collector.Type.INFO) {
                res.append("_info");
            }
            res.append(' ');
            res.append(writeEscapedHelp(metricFamilySamples.help));
            res.append('\n');

            res.append("# TYPE ");
            res.append(name);
            if (metricFamilySamples.type == Collector.Type.COUNTER) {
                res.append("_total");
            }
            if (metricFamilySamples.type == Collector.Type.INFO) {
                res.append("_info");
            }
            res.append(' ');
            res.append(typeString(metricFamilySamples.type));
            res.append('\n');

            String createdName = name + "_created";
            String gcountName = name + "_gcount";
            String gsumName = name + "_gsum";
            for (Collector.MetricFamilySamples.Sample sample : metricFamilySamples.samples) {
                /* OpenMetrics specific sample, put in a gauge at the end. */
                if (sample.name.equals(createdName)
                        || sample.name.equals(gcountName)
                        || sample.name.equals(gsumName)) {
                    Collector.MetricFamilySamples omFamily = omFamilies.get(sample.name);
                    if (omFamily == null) {
                        omFamily = new Collector.MetricFamilySamples(sample.name, Collector.Type.GAUGE, metricFamilySamples.help, new ArrayList<Collector.MetricFamilySamples.Sample>());
                        omFamilies.put(sample.name, omFamily);
                    }
                    omFamily.samples.add(sample);
                    continue;
                }
                res.append(sample.name);
                if (sample.labelNames.size() > 0) {
                    res.append('{');
                    for (int i = 0; i < sample.labelNames.size(); ++i) {
                        res.append(sample.labelNames.get(i));
                        res.append("=\"");
                        res.append(writeEscapedLabelValue(sample.labelValues.get(i)));
                        res.append("\",");
                    }
                    res.append('}');
                }
                res.append(' ');
                res.append(Collector.doubleToGoString(sample.value));
                if (sample.timestampMs != null) {
                    res.append(' ');
                    res.append(sample.timestampMs.toString());
                }
                res.append('\n');
            }
        }
        // Write out any OM-specific samples.
        if (!omFamilies.isEmpty()) {
            res.append(write004(Collections.enumeration(omFamilies.values())));
        }

        return res.toString();
    }

    private static String writeEscapedHelp(String s) {
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            switch (c) {
                case '\n':
                    res.append("\\n");
                    break;
                case '\\':
                    res.append("\\\\");
                    break;
                default:
                    res.append(c);
            }
        }
        return res.toString();
    }

    private static String typeString(Collector.Type t) {
        switch (t) {
            case GAUGE:
                return "gauge";
            case COUNTER:
                return "counter";
            case SUMMARY:
                return "summary";
            case HISTOGRAM:
                return "histogram";
            case GAUGE_HISTOGRAM:
                return "histogram";
            case STATE_SET:
                return "gauge";
            case INFO:
                return "gauge";
            default:
                return "untyped";
        }
    }

    private static String writeEscapedLabelValue(String s) throws IOException {
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            switch (c) {
                case '\n':
                    res.append("\\n");
                    break;
                case '"':
                    res.append("\\\"");
                    break;
                case '\\':
                    res.append("\\\\");
                    break;
                default:
                    res.append(c);
            }
        }
        return res.toString();
    }


}
