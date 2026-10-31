import { Overline } from '@/components/ui/Overline';
import { UnavailableState } from '@/components/ui/UnavailableState';

export default function PoliciesPage() {
  return <div className="mx-auto max-w-[1000px]"><div className="mb-6"><Overline>Governance</Overline><h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold text-fg">Review policies</h1></div><UnavailableState title="Policy management unavailable" description="No backend policy read or mutation API is available. Prototype policies have been removed from the production route." /></div>;
}
