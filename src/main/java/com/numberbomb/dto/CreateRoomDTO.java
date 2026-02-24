package com.numberbomb.dto;

import lombok.Data;

@Data
public class CreateRoomDTO {
    private String name;
    private Integer punishmentType;
    private String punishmentContent;
}
