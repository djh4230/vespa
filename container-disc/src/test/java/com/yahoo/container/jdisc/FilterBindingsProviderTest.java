// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.http.filter.FilterChainRepository;
import com.yahoo.jdisc.application.BindingRepository;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.jdisc.http.filter.SecurityResponseFilter;
import com.yahoo.jdisc.http.server.FilterBindings;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

/**
 * @author bakksjo
 */
public class FilterBindingsProviderTest {
    final ServerConfig.Builder configBuilder = new ServerConfig.Builder();

    @Test
    public void requireThatEmptyInputGivesEmptyOutput() {
        final FilterChainRepository filterChainRepository = new FilterChainRepository(
                new ChainsConfig(new ChainsConfig.Builder()),
                new ComponentRegistry<>(),
                new ComponentRegistry<>(),
                new ComponentRegistry<>(),
                new ComponentRegistry<>());

        final FilterBindingsProvider provider = new FilterBindingsProvider(
                new ComponentId("foo"),
                new ServerConfig(configBuilder),
                filterChainRepository,
                new ComponentRegistry<>());

        final FilterBindings filterBindings = provider.get();

        assertThat(filterBindings, is(not(nullValue())));
        assertThat(filterBindings.getRequestFilters().iterator().hasNext(), is(false));
        assertThat(filterBindings.getResponseFilters().iterator().hasNext(), is(false));
    }

    @Test
    public void requireThatCorrectlyConfiguredFiltersAreIncluded() {
        final String requestFilter1Id = "requestFilter1";
        final String requestFilter2Id = "requestFilter2";
        final String requestFilter3Id = "requestFilter3";
        final String responseFilter1Id = "responseFilter1";
        final String responseFilter2Id = "responseFilter2";
        final String responseFilter3Id = "responseFilter3";

        // Set up config.
        configBuilder.filter(new ServerConfig.Filter.Builder().id(requestFilter1Id).binding("http://*/a"));
        configBuilder.filter(new ServerConfig.Filter.Builder().id(requestFilter2Id).binding("http://*/b"));
        configBuilder.filter(new ServerConfig.Filter.Builder().id(responseFilter1Id).binding("http://*/c"));
        configBuilder.filter(new ServerConfig.Filter.Builder().id(responseFilter3Id).binding("http://*/d"));

        // Set up registry.
        final ComponentRegistry<RequestFilter> availableRequestFilters = new ComponentRegistry<>();
        final RequestFilter requestFilter1Instance = mock(RequestFilter.class);
        final RequestFilter requestFilter2Instance = mock(RequestFilter.class);
        final RequestFilter requestFilter3Instance = mock(RequestFilter.class);
        availableRequestFilters.register(ComponentId.fromString(requestFilter1Id), requestFilter1Instance);
        availableRequestFilters.register(ComponentId.fromString(requestFilter2Id), requestFilter2Instance);
        availableRequestFilters.register(ComponentId.fromString(requestFilter3Id), requestFilter3Instance);
        final ComponentRegistry<ResponseFilter> availableResponseFilters = new ComponentRegistry<>();
        final ResponseFilter responseFilter1Instance = mock(ResponseFilter.class);
        final ResponseFilter responseFilter2Instance = mock(ResponseFilter.class);
        final ResponseFilter responseFilter3Instance = mock(ResponseFilter.class);
        availableResponseFilters.register(ComponentId.fromString(responseFilter1Id), responseFilter1Instance);
        availableResponseFilters.register(ComponentId.fromString(responseFilter2Id), responseFilter2Instance);
        availableResponseFilters.register(ComponentId.fromString(responseFilter3Id), responseFilter3Instance);
        final FilterChainRepository filterChainRepository = new FilterChainRepository(
                new ChainsConfig(new ChainsConfig.Builder()),
                availableRequestFilters,
                availableResponseFilters,
                new ComponentRegistry<SecurityRequestFilter>(),
                new ComponentRegistry<SecurityResponseFilter>());

        // Set up the provider that we aim to test.
        final FilterBindingsProvider provider = new FilterBindingsProvider(
                new ComponentId("foo"),
                new ServerConfig(configBuilder),
                filterChainRepository,
                new ComponentRegistry<SecurityRequestFilter>());

        // Execute.
        final FilterBindings filterBindings = provider.get();

        // Verify.
        assertThat(filterBindings, is(not(nullValue())));
        assertThat(
                filterBindings.getRequestFilters(),
                containsFilters(requestFilter1Instance, requestFilter2Instance));
        assertThat(
                filterBindings.getRequestFilters(),
                not(containsFilters(requestFilter3Instance)));
        assertThat(
                filterBindings.getResponseFilters(),
                containsFilters(responseFilter1Instance, responseFilter3Instance));
        assertThat(
                filterBindings.getResponseFilters(),
                not(containsFilters(responseFilter2Instance)));
    }

    private interface DualRoleFilter extends RequestFilter, ResponseFilter {}

    @Test
    public void requireThatInstanceCanNotBeBothRequestAndResponseFilter() {
        final String filterId = "filter";

        // Set up config.
        configBuilder.filter(new ServerConfig.Filter.Builder().id(filterId).binding("http://*/*"));

        // Set up registry.
        final DualRoleFilter filterInstance = mock(DualRoleFilter.class);
        final ComponentRegistry<RequestFilter> availableRequestFilters = new ComponentRegistry<>();
        availableRequestFilters.register(ComponentId.fromString(filterId), filterInstance);
        final FilterChainRepository filterChainRepository = new FilterChainRepository(
                new ChainsConfig(new ChainsConfig.Builder()),
                availableRequestFilters,
                new ComponentRegistry<>(),
                new ComponentRegistry<>(),
                new ComponentRegistry<>());

        try {
            new FilterBindingsProvider(
                    new ComponentId("foo"),
                    new ServerConfig(configBuilder),
                    filterChainRepository,
                    new ComponentRegistry<>());
            fail("Dual-role filter should not be accepted");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("Invalid config"));
        }
    }

    @Test
    public void requireThatConfigWithUnknownReferenceFails() {
        // Set up config.
        configBuilder.filter(new ServerConfig.Filter.Builder().id("someFilter").binding("http://*/*"));

        // Set up registry.
        final FilterChainRepository filterChainRepository = new FilterChainRepository(
                new ChainsConfig(new ChainsConfig.Builder()),
                new ComponentRegistry<>(),
                new ComponentRegistry<>(),
                new ComponentRegistry<>(),
                new ComponentRegistry<>());

        try {
            new FilterBindingsProvider(
                    new ComponentId("foo"),
                    new ServerConfig(configBuilder),
                    filterChainRepository,
                    new ComponentRegistry<>());
            fail("Config with unknown filter reference should not be accepted");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("Invalid config"));
        }
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static <T> Matcher<? super BindingRepository<T>> containsFilters(
            final T... requiredInstances) {
        return new TypeSafeMatcher<BindingRepository<T>>() {
            private final Set<T> requiredFilterSet = new HashSet<>(Arrays.asList(requiredInstances));

            @Override
            protected boolean matchesSafely(final BindingRepository<T> actualInstances) {
                final Set<T> notFoundFilterSet = new HashSet<>(requiredFilterSet);
                for (final Map.Entry<UriPattern, T> actualEntry : actualInstances) {
                    notFoundFilterSet.remove(actualEntry.getValue());
                }
                return notFoundFilterSet.isEmpty();
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("BindingRepository containing " + requiredFilterSet);
            }
        };
    }
}
