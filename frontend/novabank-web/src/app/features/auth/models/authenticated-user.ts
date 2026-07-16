import { Role } from './role';

export interface AuthenticatedUser {
  id: string;
  email: string;
  roles: Role[];
}
