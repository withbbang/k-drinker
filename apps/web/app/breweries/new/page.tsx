import { BreweryForm } from "@/components/breweries/BreweryForm";
import { TopBar } from "@/components/ui/TopBar";

export default function NewBreweryPage() {
  return (
    <main className="mx-auto flex w-full max-w-sm flex-col">
      <TopBar title="새 양조장" backHref="/breweries" />
      <div className="px-4 pt-4">
        <BreweryForm />
      </div>
    </main>
  );
}
