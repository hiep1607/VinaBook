package vn.fs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductPreviewPageDto {
	private Long previewPageId;
	private Integer pageOrder;
	private Integer originalPageNumber;
	private String imageUrl;
	private Boolean enabled;
}
