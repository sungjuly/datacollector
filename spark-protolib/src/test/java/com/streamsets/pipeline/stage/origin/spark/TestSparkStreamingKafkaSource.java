/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.stage.origin.spark;


import com.google.common.collect.ImmutableList;

import org.I0Itec.zkclient.ZkClient;

import com.google.common.io.Resources;
import com.streamsets.pipeline.BootstrapSpark;
import com.streamsets.pipeline.api.ExecutionMode;
import com.streamsets.pipeline.lib.DataType;
import com.streamsets.pipeline.lib.KafkaTestUtil;
import com.streamsets.pipeline.lib.ProducerRunnable;
import com.streamsets.pipeline.main.EmbeddedPipelineFactory;
import com.streamsets.pipeline.stage.origin.kafka.KafkaDSource;
import com.streamsets.pipeline.stage.origin.spark.SparkKafkaExecutorFunction;
import com.streamsets.pipeline.stage.origin.spark.SparkStreamingBinding;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kafka.admin.AdminUtils;
import kafka.javaapi.producer.Producer;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.MockTime;
import kafka.utils.TestUtils;
import kafka.utils.TestZKUtils;
import kafka.utils.ZKStringSerializer$;
import kafka.zk.EmbeddedZookeeper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

// Todo - move this to integration test module
public class TestSparkStreamingKafkaSource {
  private static final Logger LOG = LoggerFactory.getLogger(TestSparkStreamingKafkaSource.class);
  private static String zkConnect;
  private static EmbeddedZookeeper zkServer;
  private static KafkaServer kafkaServer;
  private static ZkClient zkClient;

  private static int port;

  @Before
  public void setup() throws Exception {
    // Remove this
    System.setProperty("sdc.clustermode", "true");
    System.setProperty("sdc.testing-mode", "true");
    System.setProperty("spark.master", "local[2]"); // must be 2, not 1 (or function will never be called)
                                                    // not 3 (due to metric counter being jvm wide)
    zkConnect = TestZKUtils.zookeeperConnect();
    zkServer = new EmbeddedZookeeper(zkConnect);
    zkClient = new ZkClient(zkServer.connectString(), 30000, 30000, ZKStringSerializer$.MODULE$);
    // setup Broker
    port = TestUtils.choosePort();
    Properties props = TestUtils.createBrokerConfig(0, port);
    kafkaServer = TestUtils.createServer(new KafkaConfig(props), new MockTime());
    String metadataBrokerURI = "localhost" + ":" + port;
    File target = new File(System.getProperty("user.dir"), "target");
    Properties properties;
    File propertiesFile;
    properties = new Properties();
    properties.setProperty(EmbeddedPipelineFactory.PIPELINE_NAME, "pipeline1");
    properties.setProperty(EmbeddedPipelineFactory.PIPELINE_USER, "admin");
    properties.setProperty(EmbeddedPipelineFactory.PIPELINE_DESCRIPTION, "not much to say");
    properties.setProperty(EmbeddedPipelineFactory.PIPELINE_TAG, "unused");
    // Set this to cluster mode once the code is changed to use that
    // and change in the pipeline json file
    //properties.setProperty("sdc.runtime.mode", ExecutionMode.CLUSTER.getLabel());
    properties.setProperty(KafkaDSource.METADATA_BROKER_LIST, metadataBrokerURI);
    properties.setProperty(KafkaDSource.TOPICS, "testProduceStringRecords");
    properties.setProperty("auto.offset.reset", "smallest");
    properties.setProperty(SparkStreamingBinding.INPUT_TYPE, SparkStreamingBinding.KAFKA_INPUT_TYPE);
    propertiesFile = new File(target, "sdc.properties");
    propertiesFile.delete();
    properties.store(new FileOutputStream(propertiesFile), null);
    File pipelineJson = new File(target, "pipeline.json");
    pipelineJson.delete();
    Files.copy(Paths.get(Resources.getResource("spark_kafka_pipeline.json").toURI()),
      pipelineJson.toPath());
  }

  @After
  public void tearDown() throws Exception {
    if (kafkaServer != null) {
      kafkaServer.shutdown();
    }
    if (zkClient != null) {
      zkClient.close();
    }
  }

  @Test(timeout=120000)
  public void testSparkStreamingKafkaSource() throws Exception{
    Thread waiter = null;
    try {
      Producer<String, String> producer = KafkaTestUtil.createProducer("localhost", port, false);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneSignal = new CountDownLatch(1);
      AdminUtils.createTopic(zkClient, "testProduceStringRecords", 1, 1, new Properties());
      TestUtils.waitUntilMetadataIsPropagated(scala.collection.JavaConversions.asScalaBuffer(ImmutableList.of(kafkaServer)),
        "testProduceStringRecords", 0, 2000);
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      LOG.info("Start producing records");
      executorService.submit(new ProducerRunnable("testProduceStringRecords", 1, producer, startLatch, DataType.TEXT,
        null, 30, doneSignal));
      startLatch.countDown();
      doneSignal.await();
      LOG.info("Done producing all records");
      waiter = startBootstrapSpark();
      long i = 0;
      while (i != 30) {
        try {
          Thread.sleep(1000);
          i = SparkKafkaExecutorFunction.getRecordsProducedJVMWide();
          LOG.info(" No of records obtained till now " + i);
        } catch (Exception e) {
          String msg = "Expected exception: " + e;
          LOG.info(msg, e);
        }
      }
    } finally {
      if (waiter != null) {
       waiter.interrupt();
      }
    }

  }

  private Thread startBootstrapSpark() {
   Thread waiter = new Thread() {
      @Override
      public void run() {
        try {
          BootstrapSpark.main(new String[0]);
        } catch (Exception ex) {
          LOG.error("Error in waiter thread: " + ex, ex);
        }
      }
    };
    waiter.setName(getClass().getName() + "-Waiter");
    waiter.setDaemon(true);
    waiter.start();
    return waiter;
  }
}
