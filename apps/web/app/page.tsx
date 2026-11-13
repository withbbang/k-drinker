import { HealthStatus } from "@/components/health/HealthStatus";

export default function Home() {
  return (
    <main className="mx-auto flex max-w-xl flex-col gap-2 px-4 py-12">
      <h1 className="text-2xl font-semibold">k-drinker</h1>
      <HealthStatus />
    </main>
  );
}
