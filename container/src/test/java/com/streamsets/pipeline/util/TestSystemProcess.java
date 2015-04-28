/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.util;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.streamsets.pipeline.api.impl.Utils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TestSystemProcess {

  private static final File tempDir;

  static {
    try {
      tempDir =  File.createTempFile("test-" + TestSystemProcess.class.getSimpleName() + "-", "");
      tempDir.delete();
      tempDir.mkdir();
      Utils.checkState(tempDir.isDirectory(), "Dir " + tempDir + " does not exist");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private SystemProcess process;

  @After
  public void tearDown() {
    if (process != null) {
      process.cleanup();
    }
  }

  @Test
  public void testStart() throws Exception {
    process = new SystemProcess("sleep", tempDir, Arrays.
      asList("/bin/sleep", "1.1"));
    process.start();
    Assert.assertTrue(process.isAlive());
  }
  @Test
  public void testKill() throws Exception {
    process = new SystemProcess("sleep", tempDir, Arrays.
      asList("/bin/sleep", "3.1"));
    process.start();
    long start = System.currentTimeMillis();
    process.kill(5000);
    long elapsed = System.currentTimeMillis() - start;
    Assert.assertTrue("Expected elapsed time to be less than 3000: " + elapsed,
      elapsed < 3000);
  }
  @Test
  public void testForceKill() throws Exception {
    Assume.assumeFalse("Test only works on java 1.8 and newer, not: " + System.getProperty("java.version"),
      System.getProperty("java.version", "").contains("1.7"));
    Assume.assumeTrue("Test only works on linux, not: " + System.getProperty("os.name"),
      System.getProperty("os.name", "").trim().toLowerCase().contains("linux"));
    process = new SystemProcess("sleep", tempDir, Arrays.
      asList("/bin/bash", "-c", "trap true 15; sleep 3.1"));
    process.start();
    long start = System.currentTimeMillis();
    process.kill(5000);
    long elapsed = System.currentTimeMillis() - start;
    Assert.assertTrue("Expected elapsed time to be less than 3000: " + elapsed,
      elapsed > 3000);
  }

  @Test
  public void testOutput() throws Exception {
    process = new SystemProcess("output", tempDir, Arrays.
      asList("/bin/bash", "-c", "for i in {0..2}; do echo $i; done"));
    process.start();
    while(process.isAlive()) {
      TimeUnit.MILLISECONDS.sleep(10);
    }
    String error = Joiner.on("\n").join(process.getAllError());
    Assert.assertTrue(error, error.isEmpty());
    List<String> lines = new ArrayList<>();
    Iterables.addAll(lines, process.getOutput());
    Assert.assertEquals(Arrays.asList("0", "1", "2"), lines);
  }

  @Test
  public void testError() throws Exception {
    process = new SystemProcess("output", tempDir, Arrays.
      asList("/bin/bash", "-c", "for i in {0..2}; do echo $i 1>&2; done"));
    process.start();
    while(process.isAlive()) {
      TimeUnit.MILLISECONDS.sleep(10);
    }
    String error = Joiner.on("\n").join(process.getAllOutput());
    Assert.assertTrue(error, error.isEmpty());
    List<String> lines = new ArrayList<>();
    Iterables.addAll(lines, process.getError());
    Assert.assertEquals(Arrays.asList("0", "1", "2"), lines);
  }
}
