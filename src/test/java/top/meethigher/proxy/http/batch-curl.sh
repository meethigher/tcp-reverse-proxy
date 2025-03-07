#!/bin/bash

# 自定义 base URL，方便修改
BASE_URL="https://reqres.in/api"

# 日志打印函数
function log() {
  echo "$(date +"%Y-%m-%d %H:%M:%S"): $1"
}

# 计算时间的函数
function time_request() {
  start=$(date +%s%3N)  # 获取当前时间的毫秒
  "$@"  # 执行传入的命令
  end=$(date +%s%3N)  # 获取当前时间的毫秒
  duration=$((end - start))  # 计算请求耗时
  echo -e "\n请求耗时：${duration}ms"
}

# 1. 获取用户列表（GET）
log "请求：获取用户列表"
time_request curl -X GET "$BASE_URL/users?page=2" -H "Accept: application/json"
echo -e "\n"

# 2. 获取单个用户（GET）
log "请求：获取单个用户"
time_request curl -X GET "$BASE_URL/users/2" -H "Accept: application/json"
echo -e "\n"

# 3. 获取不存在的用户（GET - 404）
log "请求：获取不存在的用户"
time_request curl -X GET "$BASE_URL/users/23" -H "Accept: application/json"
echo -e "\n"

# 4. 获取资源列表（GET）
log "请求：获取资源列表"
time_request curl -X GET "$BASE_URL/unknown" -H "Accept: application/json"
echo -e "\n"

# 5. 获取单个资源（GET）
log "请求：获取单个资源"
time_request curl -X GET "$BASE_URL/unknown/2" -H "Accept: application/json"
echo -e "\n"

# 6. 获取不存在的资源（GET - 404）
log "请求：获取不存在的资源"
time_request curl -X GET "$BASE_URL/unknown/23" -H "Accept: application/json"
echo -e "\n"

# 7. 创建用户（POST）
log "请求：创建用户"
time_request curl -X POST "$BASE_URL/users" \
     -H "Content-Type: application/json" \
     -d '{"name": "morpheus", "job": "leader"}'
echo -e "\n"

# 8. 更新用户（PUT）
log "请求：更新用户"
time_request curl -X PUT "$BASE_URL/users/2" \
     -H "Content-Type: application/json" \
     -d '{"name": "morpheus", "job": "zion resident"}'
echo -e "\n"

# 9. 更新用户（PATCH）
log "请求：部分更新用户"
time_request curl -X PATCH "$BASE_URL/users/2" \
     -H "Content-Type: application/json" \
     -d '{"name": "morpheus", "job": "senior developer"}'
echo -e "\n"

# 10. 删除用户（DELETE）
log "请求：删除用户"
time_request curl -X DELETE "$BASE_URL/users/2"
echo -e "\n"

# 11. 用户登录（POST - 成功）
log "请求：用户登录（成功）"
time_request curl -X POST "$BASE_URL/login" \
     -H "Content-Type: application/json" \
     -d '{"email": "eve.holt@reqres.in", "password": "cityslicka"}'
echo -e "\n"

# 12. 用户登录（POST - 失败）
log "请求：用户登录（失败）"
time_request curl -X POST "$BASE_URL/login" \
     -H "Content-Type: application/json" \
     -d '{"email": "eve.holt@reqres.in"}'
echo -e "\n"

# 13. 用户注册（POST - 成功）
log "请求：用户注册（成功）"
time_request curl -X POST "$BASE_URL/register" \
     -H "Content-Type: application/json" \
     -d '{"email": "eve.holt@reqres.in", "password": "pistol"}'
echo -e "\n"

# 14. 用户注册（POST - 失败）
log "请求：用户注册（失败）"
time_request curl -X POST "$BASE_URL/register" \
     -H "Content-Type: application/json" \
     -d '{"email": "sydney@fife"}'
echo -e "\n"

# 15. 获取延迟响应的用户列表（GET - 3秒延迟）
log "请求：获取延迟响应的用户列表"
time_request curl -X GET "$BASE_URL/users?delay=3" -H "Accept: application/json"
echo -e "\n"
