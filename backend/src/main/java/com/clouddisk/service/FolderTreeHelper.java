package com.clouddisk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clouddisk.entity.Folder;
import com.clouddisk.mapper.FolderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class FolderTreeHelper {

    private final FolderMapper folderMapper;

    /** 收集子树内所有文件夹 ID（含 root，仅 deleted=0），按 userId 过滤避免跨用户数据混入 */
    public List<Long> collectActiveSubtreeIds(long userId, long rootFolderId) {
        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.offer(rootFolderId);
        while (!queue.isEmpty()) {
            Long pid = queue.poll();
            if (!visited.add(pid)) continue;  // 环检测，防重复
            result.add(pid);
            List<Folder> children = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                    .eq(Folder::getParentId, pid)
                    .eq(Folder::getDeleted, 0)
                    .eq(Folder::getUserId, userId));
            for (Folder child : children) {
                if (!visited.contains(child.getId())) {
                    queue.offer(child.getId());
                }
            }
        }
        return result;
    }

    /** 收集子树内所有文件夹 ID（含 root，仅 deleted=0），不过滤 userId（用于团队空间等跨用户共享场景） */
    public List<Long> collectActiveSubtreeIds(long rootFolderId) {
        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.offer(rootFolderId);
        while (!queue.isEmpty()) {
            Long pid = queue.poll();
            if (!visited.add(pid)) {
                log.warn("collectActiveSubtreeIds 检测到重复/环: folderId={}, rootFolderId={}", pid, rootFolderId);
                continue;
            }
            result.add(pid);
            List<Folder> children = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                    .eq(Folder::getParentId, pid)
                    .eq(Folder::getDeleted, 0));
            for (Folder child : children) {
                if (!visited.contains(child.getId())) {
                    queue.offer(child.getId());
                }
            }
        }
        return result;
    }

    /** 收集子树内所有文件夹 ID（含 root，不区分 deleted 状态） */
    public List<Long> collectAllSubtreeIds(long rootFolderId) {
        List<Long> result = new ArrayList<>();
        Queue<Long> queue = new LinkedList<>();
        queue.offer(rootFolderId);
        while (!queue.isEmpty()) {
            Long pid = queue.poll();
            result.add(pid);
            List<Folder> children = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                    .eq(Folder::getParentId, pid));
            for (Folder child : children) {
                queue.offer(child.getId());
            }
        }
        return result;
    }

    /** 收集子树内所有已回收文件夹 ID（含 root，deleted=1） */
    public List<Long> collectRecycledSubtreeIds(long userId, long rootFolderId) {
        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.offer(rootFolderId);
        while (!queue.isEmpty()) {
            Long pid = queue.poll();
            if (!visited.add(pid)) continue;
            result.add(pid);
            List<Folder> children = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                    .eq(Folder::getParentId, pid)
                    .eq(Folder::getDeleted, 1)
                    .eq(Folder::getUserId, userId));
            for (Folder child : children) {
                if (!visited.contains(child.getId())) {
                    queue.offer(child.getId());
                }
            }
        }
        return result;
    }

    public List<Long> collectRecycledSubtreeIds(long rootFolderId) {
        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.offer(rootFolderId);
        while (!queue.isEmpty()) {
            Long pid = queue.poll();
            if (!visited.add(pid)) continue;
            result.add(pid);
            List<Folder> children = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                    .eq(Folder::getParentId, pid)
                    .eq(Folder::getDeleted, 1));
            for (Folder child : children) {
                if (!visited.contains(child.getId())) {
                    queue.offer(child.getId());
                }
            }
        }
        return result;
    }

    /** 从已回收根恢复：BFS 恢复 */
    public List<Long> expandRecycledRestoreIds(long userId, long rootFolderId) {
        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.offer(rootFolderId);
        while (!queue.isEmpty()) {
            Long pid = queue.poll();
            if (!visited.add(pid)) continue;
            result.add(pid);
            List<Folder> children = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                    .eq(Folder::getParentId, pid)
                    .eq(Folder::getDeleted, 1)
                    .eq(Folder::getUserId, userId));
            for (Folder child : children) {
                if (!visited.contains(child.getId())) {
                    queue.offer(child.getId());
                }
            }
        }
        return result;
    }

    public List<Long> expandRecycledRestoreIds(long rootFolderId) {
        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.offer(rootFolderId);
        while (!queue.isEmpty()) {
            Long pid = queue.poll();
            if (!visited.add(pid)) continue;
            result.add(pid);
            List<Folder> children = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                    .eq(Folder::getParentId, pid)
                    .eq(Folder::getDeleted, 1));
            for (Folder child : children) {
                if (!visited.contains(child.getId())) {
                    queue.offer(child.getId());
                }
            }
        }
        return result;
    }
}
