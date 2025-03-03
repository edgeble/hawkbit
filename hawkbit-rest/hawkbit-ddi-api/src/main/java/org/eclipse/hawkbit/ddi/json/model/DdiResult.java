/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ddi.json.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result information of the action progress which can by an intermediate or
 * final update.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DdiResult {

    @NotNull
    @Valid
    private final FinalResult finished;

    private final DdiProgress progress;

    /**
     * Constructor.
     *
     * @param finished
     *            as final result
     * @param progress
     *            if not yet finished
     */
    @JsonCreator
    public DdiResult(@JsonProperty("finished") final FinalResult finished,
            @JsonProperty("progress") final DdiProgress progress) {
        this.finished = finished;
        this.progress = progress;
    }

    public FinalResult getFinished() {
        return finished;
    }

    public DdiProgress getProgress() {
        return progress;
    }

    /**
     * Defined status of the final result.
     *
     */
    public enum FinalResult {
        /**
         * Execution was successful.
         */
        SUCCESS("success"),

        /**
         * Execution terminated with errors or without the expected result.
         */
        FAILURE("failure"),

        /**
         * No final result could be determined (yet).
         */
        NONE("none");

        private String name;

        FinalResult(final String name) {
            this.name = name;
        }

        @JsonValue
        public String getName() {
            return name;
        }
    }

    @Override
    public String toString() {
        return "Result [finished=" + finished + ", progress=" + progress + "]";
    }

}
