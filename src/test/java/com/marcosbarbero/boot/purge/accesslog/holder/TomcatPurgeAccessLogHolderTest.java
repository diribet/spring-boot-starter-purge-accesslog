/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.marcosbarbero.boot.purge.accesslog.holder;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.web.ServerProperties.Tomcat.Accesslog;

import static com.marcosbarbero.boot.purge.accesslog.holder.TomcatPurgeAccessLogHolder.CURRENT_LOG_FILE_FIELD;
import static com.marcosbarbero.boot.purge.accesslog.holder.helper.TestUtils.createOldAccessLogFiles;
import static com.marcosbarbero.boot.purge.accesslog.holder.helper.TestUtils.getCurrentAccessLogFileName;
import static com.marcosbarbero.boot.purge.accesslog.holder.helper.TestUtils.resetTestDirectory;
import static java.nio.file.Files.createFile;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.util.ReflectionUtils.findField;
import static org.springframework.util.ReflectionUtils.makeAccessible;
import static org.springframework.util.ReflectionUtils.setField;

import com.marcosbarbero.boot.purge.accesslog.properties.PurgeProperties;

import org.apache.catalina.valves.AccessLogValve;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author Matheus Góes
 */
@RunWith(JUnit4.class)
public class TomcatPurgeAccessLogHolderTest {

	private static final String TEMP_DIR = "./target/purge_access_log_starter_test/tomcat/";
	private static final int FILES_AMOUNT = 5;

	private TomcatPurgeAccessLogHolder holderUnderTest;
	private PurgeProperties purgeProperties;
	private Accesslog accesslog;

	@Before
	public void setUp() throws Exception {
		resetTestDirectory(TEMP_DIR);

		this.purgeProperties = new PurgeProperties();
		this.purgeProperties.setEnabled(true);
		this.purgeProperties.setExecutionInterval(2);
		this.purgeProperties.setExecutionIntervalUnit(SECONDS);
		this.purgeProperties.setMaxHistory(5);
		this.purgeProperties.setMaxHistoryUnit(SECONDS);

		this.accesslog = new Accesslog();
		this.accesslog.setDirectory(TEMP_DIR);

		final Path currentAccessLogFile = Paths
				.get(TEMP_DIR + getCurrentAccessLogFileName(this.accesslog.getPrefix(),
						this.accesslog.getSuffix(), true));

		this.holderUnderTest = new TomcatPurgeAccessLogHolder(this.purgeProperties,
				Paths.get(this.accesslog.getDirectory()), this.accesslog.getPrefix(),
				this.accesslog.getSuffix(),
				this.createMockedAccessLogValve(currentAccessLogFile));

		createFile(currentAccessLogFile);

		createOldAccessLogFiles(FILES_AMOUNT, TEMP_DIR, this.accesslog.getPrefix(),
				this.accesslog.getSuffix());
	}

	@Test
	public void testCustomize() {
		this.holderUnderTest.customize(null);
		final String[] fileNames = new File(this.accesslog.getDirectory()).list();
		assertNotNull(fileNames);
		assertEquals(FILES_AMOUNT + 1, fileNames.length);
	}

	@Test
	public void testCustomizeExecutingOnStartup() throws Exception {
		this.purgeProperties.setExecuteOnStartup(true);
		this.holderUnderTest.customize(null);

		String[] fileNames = new File(this.accesslog.getDirectory()).list();
		assertNotNull(fileNames);
		assertEquals(FILES_AMOUNT + 1, fileNames.length);

		TimeUnit.SECONDS.sleep(10);
		fileNames = new File(this.accesslog.getDirectory()).list();
		assertNotNull(fileNames);
		assertEquals(1, fileNames.length);

		createOldAccessLogFiles(FILES_AMOUNT, TEMP_DIR, this.accesslog.getPrefix(),
				this.accesslog.getSuffix());

		TimeUnit.SECONDS.sleep(10);
		fileNames = new File(this.accesslog.getDirectory()).list();
		assertNotNull(fileNames);
		assertEquals(1, fileNames.length);
	}

	private AccessLogValve createMockedAccessLogValve(final Path currentAccessLogFile) {
		final AccessLogValve accessLogValve = new AccessLogValve();
		final Field field = findField(AccessLogValve.class, CURRENT_LOG_FILE_FIELD);
		makeAccessible(field);
		setField(field, accessLogValve, currentAccessLogFile.toFile());
		return accessLogValve;
	}
}