/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.fs.entity.User;
import vn.fs.config.JwtUtils;
import vn.fs.repository.UserRepository;

@Controller
@RequestMapping("/forgot-password")
public class ResetPasswordController {

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JwtUtils jwtUtils;

	@GetMapping("{token}")
	public String resetPassword(Model map, @PathVariable("token") String token) {
		User user = userRepository.findByToken(token);
		if (!valid(user, token)) {
			return "redirect:/forgot-password/error";
		}
		map.addAttribute("name", user.getName());
		map.addAttribute("email", user.getEmail());
		map.addAttribute("token", token);
		map.addAttribute("password", "");
		map.addAttribute("confirm", "");
		return "/reset-password";
	}

	@PostMapping
	public String reset(@RequestParam("password") String password, @RequestParam("confirm") String confirm,
			@RequestParam("email") String email, @RequestParam("token") String token,
			Model map) {
		User user = userRepository.findByToken(token);
		if (!valid(user, token) || !user.getEmail().equalsIgnoreCase(email)) {
			return "redirect:/forgot-password/error";
		}
		if (password.length() < 6) {
			map.addAttribute("password", password);
			map.addAttribute("confirm", confirm);
			map.addAttribute("errorPassword", "error");
			map.addAttribute("name", user.getName());
			map.addAttribute("email", user.getEmail());
			map.addAttribute("token", token);
			return "/reset-password";
		}
		if (!password.equals(confirm)) {
			map.addAttribute("errorConfirm", "error");
			map.addAttribute("name", user.getName());
			map.addAttribute("email", user.getEmail());
			map.addAttribute("password", password);
			map.addAttribute("confirm", confirm);
			map.addAttribute("token", token);
			return "/reset-password";
		}
		user.setPassword(passwordEncoder.encode(password));
		user.setStatus(true);
		user.setToken(null);
		userRepository.save(user);
		return "redirect:/forgot-password/done";
	}

	@GetMapping("/done")
	public String done() {
		return "/done";
	}

	@GetMapping("/error")
	public String error() {
		return "/error";
	}

	private boolean valid(User user, String token) {
		return user != null && jwtUtils.validatePasswordResetToken(token, user.getEmail());
	}

}
