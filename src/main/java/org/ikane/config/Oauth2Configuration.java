package org.ikane.config;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.security.oauth2.client.EnableOAuth2Sso;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

@Configuration
@EnableOAuth2Sso
public class Oauth2Configuration  extends WebSecurityConfigurerAdapter {
	
	private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
	private static final String CSRF_ANGULAR_HEADER_NAME = "X-XSRF-TOKEN";

	@Override
	public void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
			.headers()
				.frameOptions().disable()
			.and()
				.authorizeRequests()
				.anyRequest().authenticated()
				.and()
				.csrf()
					.csrfTokenRepository(csrfTokenRepository()).and()
					.addFilterAfter(csrfHeaderFilter(), CsrfFilter.class)
					;
			
			/**
			 * <logout invalidate-session="true" delete-cookies="JSESSIONID" />
			 */
			http.logout()
					.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
					//.logoutUrl("/logout")
					.logoutSuccessUrl("/")
					.invalidateHttpSession(true)
					.deleteCookies("JSESSIONID",CSRF_COOKIE_NAME, CSRF_ANGULAR_HEADER_NAME)
					;
			// @formatter:on
	}
	
	
	/**
	 * Spring security offers in-built protection for cross site request forgery
	 * (CSRF) by needing a custom token in the header for any requests that are
	 * NOT safe i.e. modify the resources from the server e.g. POST, PUT & PATCH
	 * etc.<br>
	 * <br>
	 * 
	 * This protection is achieved using cookies that send a custom value (would
	 * remain same for the session) in the first request and then the front-end
	 * would send back the value as a custom header.<br>
	 * <br>
	 * 
	 * In this method we create a filter that is applied to the web security as
	 * follows:
	 * <ol>
	 * <li>Spring security provides the CSRF token value as a request attribute;
	 * so we extract it from there.</li>
	 * <li>If we have the token, Angular wants the cookie name to be
	 * "XSRF-TOKEN". So we add the cookie if it's not there and set the path for
	 * the cookie to be "/" which is root. In more complicated cases, this might
	 * have to be the context root of the api gateway.</li>
	 * <li>We forward the request to the next filter in the chain</li>
	 * </ol>
	 * 
	 * The request-to-cookie filter that we add needs to be after the
	 * <code>csrf()</code> filter so that the request attribute for CsrfToken
	 * has been already added before we start to process it.
	 * 
	 * @return
	 */
	private Filter csrfHeaderFilter() {
		return new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
					throws ServletException, IOException {
				CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
				if (csrf != null) {
					Cookie cookie = WebUtils.getCookie(request, CSRF_COOKIE_NAME);
					String token = csrf.getToken();
					if (cookie == null
							|| token != null && !token.equals(cookie.getValue())) {
						cookie = new Cookie(CSRF_COOKIE_NAME, token);
						cookie.setPath("/");
						response.addCookie(cookie);
					}
				}
				filterChain.doFilter(request, response);
			}
		};
	}

	/**
	 * Angular sends the CSRF token in a custom header named "X-XSRF-TOKEN"
	 * rather than the default "X-CSRF-TOKEN" that Spring security expects.
	 * Hence we are now telling Spring security to expect the token in the
	 * "X-XSRF-TOKEN" header.<br><br>
	 * 
	 * This customization is added to the <code>csrf()</code> filter.
	 * 
	 * @return
	 */
	private CsrfTokenRepository csrfTokenRepository() {
		HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
		repository.setHeaderName(CSRF_ANGULAR_HEADER_NAME);
		return repository;
	}
}
