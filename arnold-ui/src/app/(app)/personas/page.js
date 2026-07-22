import { Overline } from '@/components/ui/Overline';
import { UnavailableState } from '@/components/ui/UnavailableState';

export default function PersonasPage() {
  return <div className="mx-auto max-w-[1000px]"><div className="mb-6"><Overline>Analysis configuration</Overline><h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold text-fg">Personas</h1></div><UnavailableState title="Persona catalog unavailable" description="Arnold does not expose a persona catalog, marketplace, creation, or publication API. Analysis personas may be supplied only through environment configuration." /></div>;
}
