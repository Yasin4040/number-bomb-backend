package com.numberbomb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.numberbomb.entity.VoiceGuessRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VoiceGuessRecordMapper extends BaseMapper<VoiceGuessRecord> {
    
    @Select("SELECT * FROM voice_guess_record WHERE room_id = #{roomId} AND user_id = #{userId} ORDER BY round ASC")
    List<VoiceGuessRecord> findByRoomIdAndUserId(Long roomId, String userId);
}