import NextAuth from "next-auth";
import Credentials from "next-auth/providers/credentials";

const ADMIN_USERNAME = process.env.DASHBOARD_USERNAME ?? "akin";
const ADMIN_PASSWORD = process.env.DASHBOARD_PASSWORD ?? "changeme";

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [
    Credentials({
      credentials: {
        username: { label: "Username", type: "text" },
        password: { label: "Password", type: "password" },
      },
      authorize(credentials) {
        if (
          credentials?.username === ADMIN_USERNAME &&
          credentials?.password === ADMIN_PASSWORD
        ) {
          return { id: "1", name: "Akın Coşkun", email: "akin@outreach.local" };
        }
        return null;
      },
    }),
  ],
  pages: {
    signIn: "/login",
  },
  session: { strategy: "jwt" },
  secret: process.env.AUTH_SECRET ?? "dev-auth-secret-change-in-prod",
});
