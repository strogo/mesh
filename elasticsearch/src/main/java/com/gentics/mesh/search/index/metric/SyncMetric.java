package com.gentics.mesh.search.index.metric;

import com.gentics.mesh.metric.Metric;

/**
 * Metric POJO for elasticsearch sync operation.
 */
public class SyncMetric implements Metric {

	private final Operation operation;
	private final Meter meter;
	private final String type;

	public SyncMetric(String type, Operation operation, Meter meter) {
		this.operation = operation;
		this.meter = meter;
		this.type = type;
	}

	@Override
	public String key() {
		return String.format("mesh_index_sync_%s_%s_%s", type, operation.name().toLowerCase(), meter.name().toLowerCase());
	}

	@Override
	public String description() {
		return null;
	}

	/**
	 * Operation type.
	 */
	public enum Operation {
		INSERT, UPDATE, DELETE,
	}

	/**
	 * Meter variation.
	 */
	public enum Meter {
		PENDING, SYNCED
	}
}
