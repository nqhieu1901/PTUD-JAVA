export function replaceAdminUser(users, updatedUser) {
  return users.map((user) => user.id === updatedUser.id ? updatedUser : user);
}

export function canChangeAccountStatus(currentUser, targetUser) {
  return currentUser?.role === "ADMIN" && currentUser.id !== targetUser?.id;
}

export function accountStatusLabel(status) {
  return status === "LOCKED" ? "Đã khóa" : "Hoạt động";
}
