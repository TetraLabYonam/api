import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from './Modal';

describe('Modal', () => {
  afterEach(() => {
    cleanup();
  });

  it('제목과 children을 렌더링한다', () => {
    render(
      <Modal title="테스트 모달" onClose={vi.fn()}>
        <p>내용</p>
      </Modal>
    );
    expect(screen.getByRole('dialog', { name: '테스트 모달' })).toBeInTheDocument();
    expect(screen.getByText('내용')).toBeInTheDocument();
  });

  it('닫기 버튼 클릭 시 onClose를 호출한다', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(
      <Modal title="테스트 모달" onClose={onClose}>
        <p>내용</p>
      </Modal>
    );
    await user.click(screen.getByRole('button', { name: '닫기' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('ESC 키 입력 시 onClose를 호출한다', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(
      <Modal title="테스트 모달" onClose={onClose}>
        <p>내용</p>
      </Modal>
    );
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
