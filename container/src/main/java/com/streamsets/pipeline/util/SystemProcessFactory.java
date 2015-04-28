/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.util;

import java.io.File;
import java.util.List;

public class SystemProcessFactory {

  public SystemProcess create(String name, File tempDir, List<String> args) {
    return new SystemProcess(name, tempDir, args);
  }
}
