/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.runners.direct;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.theInstance;
import static org.junit.Assert.assertThat;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.AvroIO;
import org.apache.beam.sdk.io.AvroIOTest;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PDone;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/**
 * Tests for {@link AvroIOShardedWriteFactory}.
 */
@RunWith(JUnit4.class)
public class AvroIOShardedWriteFactoryTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  private AvroIOShardedWriteFactory factory;

  @Before
  public void setup() {
    factory = new AvroIOShardedWriteFactory();
  }

  @Test
  public void originalWithoutShardingReturnsOriginal() throws Exception {
    File file = tmp.newFile("foo");
    PTransform<PCollection<String>, PDone> original =
        AvroIO.Write.withSchema(String.class).to(file.getAbsolutePath()).withoutSharding();
    PTransform<PCollection<String>, PDone> overridden = factory.override(original);

    assertThat(overridden, theInstance(original));
  }

  @Test
  public void originalShardingNotSpecifiedReturnsOriginal() throws Exception {
    File file = tmp.newFile("foo");
    PTransform<PCollection<String>, PDone> original =
        AvroIO.Write.withSchema(String.class).to(file.getAbsolutePath());
    PTransform<PCollection<String>, PDone> overridden = factory.override(original);

    assertThat(overridden, theInstance(original));
  }

  @Test
  public void originalShardedToOneReturnsExplicitlySharded() throws Exception {
    File file = tmp.newFile("foo");
    AvroIO.Write.Bound<String> original =
        AvroIO.Write.withSchema(String.class).to(file.getAbsolutePath()).withNumShards(1);
    PTransform<PCollection<String>, PDone> overridden = factory.override(original);

    assertThat(overridden, not(Matchers.<PTransform<PCollection<String>, PDone>>equalTo(original)));

    Pipeline p = getPipeline();
    String[] elems = new String[] {"foo", "bar", "baz"};
    p.apply(Create.<String>of(elems)).apply(overridden);

    file.delete();

    p.run();
    AvroIOTest.assertTestOutputs(elems, 1, file.getAbsolutePath(), original.getShardNameTemplate());
  }

  @Test
  public void originalShardedToManyReturnsExplicitlySharded() throws Exception {
    File file = tmp.newFile("foo");
    AvroIO.Write.Bound<String> original =
        AvroIO.Write.withSchema(String.class).to(file.getAbsolutePath()).withNumShards(3);
    PTransform<PCollection<String>, PDone> overridden = factory.override(original);

    assertThat(overridden, not(Matchers.<PTransform<PCollection<String>, PDone>>equalTo(original)));

    Pipeline p = getPipeline();
    String[] elems = new String[] {"foo", "bar", "baz", "spam", "ham", "eggs"};
    p.apply(Create.<String>of(elems)).apply(overridden);

    file.delete();
    p.run();
    AvroIOTest.assertTestOutputs(elems, 3, file.getAbsolutePath(), original.getShardNameTemplate());
  }

  private Pipeline getPipeline() {
    PipelineOptions options = TestPipeline.testingPipelineOptions();
    options.setRunner(InProcessPipelineRunner.class);
    return TestPipeline.fromOptions(options);
  }
}
