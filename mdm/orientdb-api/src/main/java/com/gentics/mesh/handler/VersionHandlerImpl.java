package com.gentics.mesh.handler;

import static com.gentics.mesh.core.rest.error.Errors.error;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.MeshVersion;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.rest.error.GenericRestException;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import static com.gentics.mesh.shared.SharedKeys.API_VERSION_CONTEXT_KEY;

/**
 * Puts the requested API version in the routing context with the key {@link #API_VERSION_CONTEXT_KEY}
 */
@Singleton
public class VersionHandlerImpl implements VersionHandler {

	public static final String API_MOUNTPOINT = "/api/:apiversion/*";

	private static final Pattern versionRegex = Pattern.compile("^v(\\d+)$");

	@Inject
	public VersionHandlerImpl() {
	}

	@Override
	public void handle(RoutingContext event) {
		event.put(API_VERSION_CONTEXT_KEY, parseVersion(event));
		event.next();
	}

	private int parseVersion(RoutingContext event) {
		String strVersion = event.pathParam(API_VERSION_CONTEXT_KEY);
		Matcher matcher = versionRegex.matcher(strVersion);
		matcher.find();
		try {
			int version = Integer.parseInt(matcher.group(1));
			if (version < 1 || version > MeshVersion.CURRENT_API_VERSION) {
				throw notFoundError(strVersion);
			}
			return version;
		} catch (RuntimeException ex) {
			throw notFoundError(strVersion);
		}
	}

	private GenericRestException notFoundError(String strVersion) {
		return error(HttpResponseStatus.NOT_FOUND, "error_version_not_found", strVersion, "v" + MeshVersion.CURRENT_API_VERSION);
	}

	/**
	 * Creates the start of a route for specific version. Example: /api/v2
	 * 
	 * @param version
	 * @return
	 */
	public static String baseRoute(int version) {
		return "/api/v" + version;
	}

	/**
	 * A stream that generates all available baseRoutes. Example: ["/api/v1", "/api/v2"]
	 * 
	 * @return
	 */
	public static Stream<String> generateVersionMountpoints() {
		return IntStream.rangeClosed(1, MeshVersion.CURRENT_API_VERSION)
			.mapToObj(VersionHandlerImpl::baseRoute);
	}

	/**
	 * Return the basepath for the given action context.
	 * 
	 * @param ac
	 * @return API Basepath
	 */
	public static String baseRoute(InternalActionContext ac) {
		return baseRoute(ac.getApiVersion());
	}
}
