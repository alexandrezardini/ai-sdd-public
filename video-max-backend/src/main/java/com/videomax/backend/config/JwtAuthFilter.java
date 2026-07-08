package com.videomax.backend.config;

import jakarta.servlet.Filter;

/**
 * Contract for the JWT authentication filter contributed by the {@code auth}
 * module. {@link SecurityConfig} wires the filter into the chain through this
 * interface so the cross-cutting {@code config} module never depends on the
 * {@code auth} module — keeping the module dependency one-directional
 * ({@code auth} → {@code config}) and free of cycles.
 */
public interface JwtAuthFilter extends Filter {
}
