package com.numberbomb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.numberbomb.entity.GameRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GameRecordMapper extends BaseMapper<GameRecord> {
}
