package com.clouddisk.service;

import com.clouddisk.common.BusinessException;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.Folder;
import com.clouddisk.entity.TeamMember;
import com.clouddisk.entity.TeamSpace;
import com.clouddisk.mapper.FileMapper;
import com.clouddisk.mapper.FolderMapper;
import com.clouddisk.mapper.TeamMemberMapper;
import com.clouddisk.mapper.TeamSpaceMapper;
import com.clouddisk.team.TeamRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamAccessServiceTest {

    @Mock private TeamSpaceMapper teamSpaceMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private FolderMapper folderMapper;
    @Mock private FileMapper fileMapper;
    @Mock private FolderTreeHelper folderTreeHelper;
    @Spy @InjectMocks private TeamAccessService teamAccessService;

    @Test
    void requireWrite_viewer_throws() {
        stubTeamContext(TeamRole.VIEWER);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamAccessService.requireWrite(20L, 2L));
        assertTrue(ex.getMessage().contains("只读"));
    }

    @Test
    void requireDeleteFile_member_canDeleteOwnFile() {
        stubTeamContext(TeamRole.MEMBER);
        FileRecord file = new FileRecord();
        file.setId(1L);
        file.setUserId(2L);
        file.setFolderId(20L);
        assertDoesNotThrow(() -> teamAccessService.requireDeleteFile(file, 2L));
    }

    @Test
    void requireDeleteFile_member_cannotDeleteOthersFile() {
        stubTeamContext(TeamRole.MEMBER);
        FileRecord file = new FileRecord();
        file.setId(1L);
        file.setUserId(99L);
        file.setFolderId(20L);
        assertThrows(BusinessException.class, () -> teamAccessService.requireDeleteFile(file, 2L));
    }

    @Test
    void checkTeamQuota_exceedsLimit_throws() {
        TeamSpace space = new TeamSpace();
        space.setId(1L);
        space.setRootFolderId(10L);
        space.setMaxSize(1000L);
        space.setStatus(1);

        Folder root = new Folder();
        root.setId(10L);
        root.setParentId(0L);
        when(folderMapper.selectById(10L)).thenReturn(root);
        when(teamSpaceMapper.selectOne(any())).thenReturn(space);
        doReturn(900L).when(teamAccessService).calculateTeamUsedBytes(10L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamAccessService.checkTeamQuota(10L, 200L));
        assertTrue(ex.getMessage().contains("团队存储空间不足"));
    }

    private void stubTeamContext(String role) {
        TeamSpace space = new TeamSpace();
        space.setId(1L);
        space.setRootFolderId(10L);
        space.setStatus(1);

        Folder folder = new Folder();
        folder.setId(20L);
        folder.setParentId(10L);

        Folder root = new Folder();
        root.setId(10L);
        root.setParentId(0L);

        lenient().when(folderMapper.selectById(20L)).thenReturn(folder);
        lenient().when(folderMapper.selectById(10L)).thenReturn(root);
        lenient().when(teamSpaceMapper.selectOne(any())).thenReturn(space);

        TeamMember member = new TeamMember();
        member.setSpaceId(1L);
        member.setUserId(2L);
        member.setRole(role);
        lenient().when(teamMemberMapper.selectOne(any())).thenReturn(member);
    }
}
