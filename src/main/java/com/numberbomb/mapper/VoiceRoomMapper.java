package com.numberbomb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.numberbomb.entity.VoiceRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VoiceRoomMapper extends BaseMapper<VoiceRoom> {
    
    @Select("SELECT * FROM voice_room WHERE room_number = #{roomNumber} AND status != 'ENDED' ORDER BY created_at DESC LIMIT 1")
    VoiceRoom findByRoomNumber(String roomNumber);
}