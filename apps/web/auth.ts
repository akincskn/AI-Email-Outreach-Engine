import NextAuth from "next-auth";
import Credentials from "next-auth/providers/credentials";

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [
    Credentials({
      credentials: {
        username: { label: "Username", type: "text" },
        password: { label: "Password", type: "password" },
      },
      authorize(credentials) {
        const expectedUsername = process.env.DASHBOARD_USERNAME;
        const expectedPassword = process.env.DASHBOARD_PASSWORD;
        if (!expectedUsername || !expectedPassword) {
          throw new Error("Dashboard credentials not configured");
        }
        if (
          credentials?.username === expectedUsername &&
          credentials?.password === expectedPassword
        ) {
          return {
            id: "akin",
            name: "Akın Coşkun",
            email: process.env.MAIL_USERNAME ?? "akin@local",
          };
        }
        return null;
      },
    }),
  ],
  pages: {
    signIn: "/login",
  },
  session: { strategy: "jwt", maxAge: 30 * 24 * 60 * 60 },
  secret: process.env.AUTH_SECRET,
  callbacks: {
    // Drives the middleware: every route except /login requires a logged-in user.
    authorized({ auth, request }) {
      const isLoggedIn = !!auth?.user;
      if (request.nextUrl.pathname === "/login") return true;
      return isLoggedIn;
    },
  },
});
