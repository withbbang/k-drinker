import { InstagramTokenPanel } from "@/components/settings/InstagramTokenPanel";
import { LogoutButton } from "@/components/settings/LogoutButton";

export default function SettingsPage() {
  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">설정</h1>
      <InstagramTokenPanel />
      <LogoutButton />
    </>
  );
}
