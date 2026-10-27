package org.naptune.payments.payout.simulation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Run app with the "scenario" profile and every payout request meets the brief's
// 09:30 world, so docs/payout-api.http gets the responses the documentation
// shows. Seeding once at startup is not enough: the scenario's times are
// relative to the clock, and after a minute the funds read would be stale.
// Tests call world.scenario() themselves and never activate this profile.
@Component
@Profile("scenario")
class ScenarioSeedFilter extends OncePerRequestFilter {

    private final SimulatedWorld world;

    ScenarioSeedFilter(SimulatedWorld world) {
        this.world = world;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/payments");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        world.scenario();
        chain.doFilter(request, response);
    }
}
