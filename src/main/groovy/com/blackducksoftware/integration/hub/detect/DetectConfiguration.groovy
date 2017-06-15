/*
 * Copyright (C) 2017 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.blackducksoftware.integration.hub.detect

import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.stereotype.Component

import com.blackducksoftware.integration.hub.detect.bomtool.BomTool
import com.blackducksoftware.integration.hub.detect.bomtool.DockerBomTool
import com.blackducksoftware.integration.hub.detect.exception.DetectException
import com.blackducksoftware.integration.hub.detect.type.BomToolType

@Component
class DetectConfiguration {
    private final Logger logger = LoggerFactory.getLogger(DetectProperties.class)

    static final String DOCKER_PROPERTY_PREFIX = 'detect.docker.passthrough.'

    @Autowired
    ConfigurableEnvironment configurableEnvironment

    @Autowired
    DetectProperties detectProperties

    @Autowired
    DockerBomTool dockerBomTool

    Set<String> additionalDockerPropertyNames = new HashSet<>()

    private boolean usingDefaultSourcePaths
    private boolean usingDefaultOutputPath

    void init() {
        if (detectProperties.sourcePaths == null || detectProperties.sourcePaths.length == 0) {
            usingDefaultSourcePaths = true
            detectProperties.sourcePaths = [
                System.getProperty('user.dir')
            ] as String[]
        }

        if (StringUtils.isBlank(detectProperties.outputDirectoryPath)) {
            usingDefaultOutputPath = true
            detectProperties.outputDirectoryPath = System.getProperty('user.home') + File.separator + 'blackduck'
        }

        detectProperties.nugetInspectorPackageName = detectProperties.nugetInspectorPackageName.trim()
        detectProperties.nugetInspectorPackageVersion = detectProperties.nugetInspectorPackageVersion.trim()

        File outputDirectory = new File(detectProperties.outputDirectoryPath)
        outputDirectory.mkdirs()
        if (!outputDirectory.exists() || !outputDirectory.isDirectory()) {
            throw new DetectException("The output directory ${detectProperties.outputDirectoryPath} does not exist. The system property 'user.home' will be used by default, but the output directory must exist.")
        }
        detectProperties.outputDirectoryPath = detectProperties.outputDirectoryPath.trim()

        if (dockerBomTool.isBomToolApplicable()) {
            configureForDocker()
        }
    }

    /**
     * If the default source path is being used AND docker is configured, don't run unless the tool is docker
     */
    public boolean shouldRun(BomTool bomTool) {
        if (usingDefaultSourcePaths && dockerBomTool.isBomToolApplicable()) {
            return BomToolType.DOCKER == bomTool.bomToolType
        } else {
            return true
        }
    }

    public String getDetectProperty(String key) {
        configurableEnvironment.getProperty(key)
    }

    private void configureForDocker() {
        if (!detectProperties.dockerInstallPath) {
            detectProperties.dockerInstallPath = detectProperties.outputDirectoryPath + File.separator + 'docker-install'
        }

        if (!detectProperties.dockerSandboxPath) {
            detectProperties.dockerSandboxPath = detectProperties.dockerInstallPath + File.separator + 'sandbox'
        }

        File dockerInstallDirectory = new File(detectProperties.dockerInstallPath)
        dockerInstallDirectory.mkdirs()

        File dockerSandboxDirectory = new File(detectProperties.dockerSandboxPath)
        dockerSandboxDirectory.mkdirs()

        MutablePropertySources mutablePropertySources = configurableEnvironment.getPropertySources()
        mutablePropertySources.each { propertySource ->
            if (propertySource instanceof EnumerablePropertySource) {
                EnumerablePropertySource enumerablePropertySource = (EnumerablePropertySource) propertySource
                enumerablePropertySource.propertyNames.each { propertyName ->
                    if (propertyName && propertyName.startsWith(DOCKER_PROPERTY_PREFIX)) {
                        additionalDockerPropertyNames.add(propertyName)
                    }
                }
            }
        }
    }
}
