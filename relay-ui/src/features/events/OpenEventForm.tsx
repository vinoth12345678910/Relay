import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Field, monoInputClass } from '@/components/ui/Field';

const schema = z.object({ id: z.string().trim().pipe(z.guid('Enter an event id (a UUID)')) });

export function OpenEventForm() {
  const navigate = useNavigate();
  const { register, handleSubmit, formState } = useForm<
    z.input<typeof schema>,
    unknown,
    z.output<typeof schema>
  >({
    resolver: zodResolver(schema),
    defaultValues: { id: '' },
  });
  return (
    <form
      onSubmit={handleSubmit((v) => navigate(`/events/${v.id}`))}
      noValidate
      className="flex items-end gap-2"
    >
      <Field label="Open an event by id" error={formState.errors.id?.message} className="flex-1">
        <input
          {...register('id')}
          className={monoInputClass}
          placeholder="4f1c…"
          autoComplete="off"
          spellCheck={false}
        />
      </Field>
      <Button type="submit" className={formState.errors.id ? 'mb-6' : undefined}>
        Open
      </Button>
    </form>
  );
}
