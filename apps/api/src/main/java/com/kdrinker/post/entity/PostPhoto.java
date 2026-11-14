package com.kdrinker.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "post_photo")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostPhoto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id")
	private Post post;

	private int position;

	@Column(name = "original_path")
	private String originalPath;

	@Column(name = "rendered_path")
	private String renderedPath;

	@Column(name = "public_token", length = 43, unique = true)
	private String publicToken;

	@Column(name = "focus_x")
	private Double focusX;

	@Column(name = "focus_y")
	private Double focusY;

	@Column(name = "focus_w")
	private Double focusW;

	@Column(name = "focus_h")
	private Double focusH;

	@Enumerated(EnumType.STRING)
	@Column(name = "fit_mode")
	private FitMode fitMode;

	public static PostPhoto of(Post post, int position, String originalPath, String publicToken, FitMode fitMode) {
		PostPhoto photo = new PostPhoto();
		photo.post = post;
		photo.position = position;
		photo.originalPath = originalPath;
		photo.publicToken = publicToken;
		photo.fitMode = fitMode;
		return photo;
	}

	public void changeFocus(Double x, Double y, Double w, Double h) {
		this.focusX = x;
		this.focusY = y;
		this.focusW = w;
		this.focusH = h;
	}

	public void changeFitMode(FitMode fitMode) {
		this.fitMode = fitMode;
	}

	public void attachRendered(String renderedPath) {
		this.renderedPath = renderedPath;
	}

}
