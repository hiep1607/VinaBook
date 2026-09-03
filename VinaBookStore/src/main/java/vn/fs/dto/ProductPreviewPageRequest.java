package vn.fs.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Data;

@Data
public class ProductPreviewPageRequest {
	@NotBlank
	@Size(max = 1000)
	private String imageUrl;

	@NotNull
	@Min(1)
	@Max(10)
	private Integer pageOrder;

	@Min(1)
	private Integer originalPageNumber;

	private Boolean enabled = Boolean.TRUE;
}
