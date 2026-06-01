import { Navigate, useLocation } from "react-router-dom";
import { getAccessToken } from "@/api/client";
import { useMe } from "@/hooks/useAuth";

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const hasToken = Boolean(getAccessToken());
  const { data, isLoading, isError } = useMe();

  if (!hasToken) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (isLoading) {
    return <SplashScreen />;
  }
  if (isError || !data) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <>{children}</>;
}

function SplashScreen() {
  return (
    <div className="grid min-h-full place-items-center">
      <div className="flex items-center gap-3 text-lg font-semibold animate-pulse">
        <img src="/logo.svg" alt="" className="size-9" />
        <span>MyHealth</span>
      </div>
    </div>
  );
}
