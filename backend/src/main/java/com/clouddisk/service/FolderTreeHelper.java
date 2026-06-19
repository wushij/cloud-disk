package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clouddisk.entity.Folder;
import com.clouddisk.mapper.FolderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class FolderTreeHelper {

    private final FolderMapper folderMapper;

    /** 收集子树内所有文件夹 ID（含 root，仅 deleted=0） */
    public List<Long> collectActiveSubtreeIds(long userId, long rootFolderId) {
        return collectActiveSubtreeIds(rootFolderId);
    }

    public List<Long> collectActiveSubtreeIds(long rootFolderId) {
        List<Long> result = new ArrayList<>();
        Queue<Long> queue = new LinkedList<>();
        queue.offer(rootFolderId);
        while (!queue.isEmpty()) {
            Long pid = queue.poll();
            result.add(pid);
            List<Folder> children = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                    .eq(Folder::getParentId, pid)
                    .eq(Folder::getDeleted, 0));
            for (Folder child : children) {
                queue.offer(child.getId());
            }
        }
        return result;
    }

    /** 收集子树内所有已回收文件夹 ID（含 root，deleted=1） */
    public List<Long> collectRecycledSubtreeIds(long userId, long rootFolderId) {
        return collectRecycledSubtreeIds(rootFolderId);
    }

    public List<Long> collectRecycledSubtreeIds(long rootFolderId) {
        List<Long> result = new ArrayList<>();
        Queue<Long> queue = new LinkedList<>();
        queue.offer(rootFolderId);
        while (!queue.isEmpty()) {
            Long pid = queue.poll();
            result.add(pid);
            List<Folder> children = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                    .eq(Folder::getParentId, pid)
                    .eq(Folder::getDeleted, 1));
            for (Folder child : children) {
                queue.offer(child.getId());
            }
        }
        return result;
    }

    /** 从已回收根恢复：BFS 恢复 */
    public List<Long> expandRecycledRestoreIds(long userId, long rootFolderId) {
        return expandRecycledRestoreIds(rootFolderId);
    }

    public List<Long> expandRecycledRestoreIds(long rootFolderId) {
        List<Long> result = new ArrayList<>();
        Queue<Long> queue = new LinkedList<>();
        queue.offer(rootFolderId);
        while (!queue.isEmpty()) {
            Long pid = queue.poll();
            result.add(pid);
            List<Folder> children = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                    .eq(Folder::getParentId, pid)
                    .eq(Folder::getDeleted, 1));
            for (Folder child : children) {
                queue.offer(child.getId());
            }
        }
        return result;
    }
}
