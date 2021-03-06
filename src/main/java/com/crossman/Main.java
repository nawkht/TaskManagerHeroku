/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crossman;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.sql2o.Sql2o;

import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;

@Controller
@SpringBootApplication
public class Main {
	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	@Autowired
	private AuthenticationSetter authenticationSetter;

	@Autowired
	private Encoderator encoderator;

	@Autowired
	private TaskRepository taskRepository;

	public static void main(String[] args) throws Exception {
		SpringApplication.run(Main.class, args);
	}

	@RequestMapping(value = "/save", method = RequestMethod.POST)
	String save(HttpSession session, Model model, @RequestBody List<Task> tasks) {
		final String username = getUsernameFromSession(session);
		logger.debug("save({},{})", username, tasks);
		if (username == null) {
			logger.debug("redirecting to login page");
			return "redirect:/login";
		}
		model.addAttribute("tasks", tasks);
		taskRepository.setTasksByUsername(username, tasks);
		return todo(session,model);
	}

	@RequestMapping(value = "/signup", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT})
	String signup(@ModelAttribute Auth auth, Model model, RedirectAttributes redirectAttributes) {
		logger.debug("signup({})", auth);
		if (auth == null || auth.getUsername() == null || auth.getPassword() == null) {
			logger.debug("rendering signup page");
			return "signup";
		}
		try {
			logger.debug("marking {} as authorized user", auth.getUsername());
			authenticationSetter.setAuthorized(auth);

			logger.trace("logging in as new user");
			SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(auth.getUsername(),encoderator.encode(auth.getPassword()),Collections.singletonList(GrantedAuthorities.USER)));

			logger.trace("redirect attributes set");
			redirectAttributes.addFlashAttribute("infos", Collections.singletonList("Signup was a success!"));

			logger.debug("redirecting to todo page");
			return "redirect:/todo";
		} catch (Exception e) {
			logger.error("There was a problem during signup.", e);
			model.addAttribute("errors", Collections.singletonList("There was a problem during signup."));

			logger.debug("rendering signup page");
			return "signup";
		}
	}

	@RequestMapping("/todo")
	String todo(HttpSession session, Model model) {
		final String username = getUsernameFromSession(session);
		logger.debug("todo({})", username);
		if (username == null) {
			logger.debug("redirecting to login page");
			return "redirect:/login";
		}
		if (!model.containsAttribute("tasks")) {
			final List<Task> tasks = taskRepository.getTasksByUsername(username);
			logger.trace("tasks = {}", tasks);
			model.addAttribute("tasks", tasks);
		}
		logger.debug("rendering todo page");
		return "todo";
	}

	private static String getUsernameFromSession(HttpSession session) {
		if (session == null) {
			return null;
		}
		final Object o = session.getAttribute("SPRING_SECURITY_CONTEXT");
		if (o == null) {
			return null;
		} else if (o instanceof SecurityContext) {
			final Authentication authentication = ((SecurityContext) o).getAuthentication();
			return authentication == null ? null : authentication.getName();
		} else {
			logger.error("Unexpected security context type '{}'", o.getClass());
			return null;
		}
	}

	@Bean
	public DataSource dataSource() {
		String db  = System.getenv("db-url");
		String usr = System.getenv("db-username");
		String pwd = System.getenv("db-password");

		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(db + "?user=" + usr + "&password=" + pwd);
		config.setDriverClassName("org.postgresql.Driver");
		return new HikariDataSource(config);
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Bean
	public SpringLiquibase liquibase() {
		SpringLiquibase liquibase = new SpringLiquibase();
		liquibase.setChangeLog("classpath:dbchangelog.xml");
		liquibase.setDataSource(dataSource());
		return liquibase;
	}

	@Bean
	public Sql2o sql2o() {
		return new Sql2o(dataSource());
	}
}
