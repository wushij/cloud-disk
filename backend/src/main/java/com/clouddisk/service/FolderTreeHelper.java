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
        Map<Long, List<Folder>> byParent = loadFoldersByParent(userId, 0);
        List<Long> result = new ArrayList<>();
        collectIds(rootFolderId, byParent, result);
        return result;
    }

    /** 收集子树内所有已回收文件夹 ID（含 root，deleted=1） */
    public List<Long> collectRecycledSubtreeIds(long userId, long rootFolderId) {
        Map<Long, List<Folder>> byParent = loadFoldersByParent(userId, 1);
        List<Long> result = new ArrayList<>();
        collectIds(rootFolderId, byParent, result);
        return result;
    }

    /** 从已回收根恢复：BFS 扩展 deleted=1 且父级已在集合中的节点 */
    public List<Long> expandRecycledRestoreIds(long userId, long rootFolderId) {
        List<Folder> recycled = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getUserId, userId)
                .eq(Folder::getDeleted, 1));
        Map<Long, List<Folder>> byParent = new HashMap<>();
        for (Folder f : recycled) {
            byParent.computeIfAbsent(f.getParentId(), k -> new ArrayList<>()).add(f);
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        ids.add(rootFolderId);
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootFolderId);
        while (!queue.isEmpty()) {
            Long pid = queue.poll();
            for (Folder child : byParent.getOrDefault(pid, List.of())) {
                if (ids.add(child.getId())) {
                    queue.add(child.getId());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private Map<Long, List<Folder>> loadFoldersByParent(long userId, int deleted) {
        List<Folder> all = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getUserId, userId)
                .eq(Folder::getDeleted, deleted));
        Map<Long, List<Folder>> byParent = new HashMap<>();
        for (Folder f : all) {
            byParent.computeIfAbsent(f.getParentId(), k -> new ArrayList<>()).add(f);
        }
        return byParent;
    }

    private void collectIds(long folderId, Map<Long, List<Folder>> byParent, List<Long> result) {
        result.add(folderId);
        for (Folder child : byParent.getOrDefault(folderId, List.of())) {
            collectIds(child.getId(), byParent, result);
        }
    }
}
