package com.numberbomb.dto;

import lombok.Data;

@Data
public class CreateRoomDTO {
    private String name;
    private Integer punishmentType;
    private String punishmentContent;
    /**
     * 房间类型：1-普通对战，2-语音对战
     */
    private Integer roomType;
}
