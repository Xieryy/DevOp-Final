package net.orderzone.idcard.dto;

import lombok.Getter;
import lombok.Setter;
import net.orderzone.idcard.model.Profile;
import net.orderzone.idcard.model.Template;

@Getter
@Setter
public class IdCardPreviewRequest {
    private Profile profile;
    private Template template;
}