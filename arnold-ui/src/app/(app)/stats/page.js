import { Overline } from '@/components/ui/Overline';
import { UnavailableState } from '@/components/ui/UnavailableState';

export default function StatsPage() {
  return <div className="mx-auto max-w-[1000px]"><div className="mb-6"><Overline>Reporting</Overline><h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold text-fg">Stats</h1></div><UnavailableState title="Statistics unavailable" description="Arnold does not currently expose personal, organization, or administrative statistics read APIs." /></div>;
}
