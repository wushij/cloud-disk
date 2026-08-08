package com.clouddisk.util;

import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.Folder;
import com.clouddisk.entity.QuotaApplication;
import com.clouddisk.entity.TeamSpace;
import com.clouddisk.entity.User;
import com.clouddisk.vo.FileVO;
import com.clouddisk.vo.FolderVO;
import com.clouddisk.vo.QuotaApplicationVO;
import com.clouddisk.vo.TeamSpaceVO;
import com.clouddisk.vo.UserVO;
import org.springframework.beans.BeanUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class VOMapper {

    public static UserVO toUserVO(User user) {
        if (user == null) return null;
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    public static FileVO toFileVO(FileRecord file) {
        if (file == null) return null;
        FileVO vo = new FileVO();
        BeanUtils.copyProperties(file, vo);
        return vo;
    }

    public static List<FileVO> toFileVOList(List<FileRecord> files) {
        if (files == null) return Collections.emptyList();
        return files.stream().map(VOMapper::toFileVO).collect(Collectors.toList());
    }

    public static FolderVO toFolderVO(Folder folder) {
        if (folder == null) return null;
        FolderVO vo = new FolderVO();
        BeanUtils.copyProperties(folder, vo);
        return vo;
    }

    public static List<FolderVO> toFolderVOList(List<Folder> folders) {
        if (folders == null) return Collections.emptyList();
        return folders.stream().map(VOMapper::toFolderVO).collect(Collectors.toList());
    }

    public static TeamSpaceVO toTeamSpaceVO(TeamSpace space) {
        if (space == null) return null;
        TeamSpaceVO vo = new TeamSpaceVO();
        BeanUtils.copyProperties(space, vo);
        return vo;
    }

    public static QuotaApplicationVO toQuotaApplicationVO(QuotaApplication qa) {
        if (qa == null) return null;
        QuotaApplicationVO vo = new QuotaApplicationVO();
        BeanUtils.copyProperties(qa, vo);
        vo.setApprovalOpinion(qa.getApprovalOpinion());
        return vo;
    }
}
